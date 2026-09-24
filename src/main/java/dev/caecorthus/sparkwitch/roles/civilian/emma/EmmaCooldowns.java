package dev.caecorthus.sparkwitch.roles.civilian.emma;

import dev.caecorthus.sparkwitch.compat.SparkTraitsGunBridge;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownEntryAccessor;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownManagerAccessor;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;

/** Exact fallback writes bypass set-time multipliers but retain vanilla client synchronization.
 * 精确回退写入不重复应用倍率，仍保留原版客户端冷却同步。 */
public final class EmmaCooldowns {
    private static final ThreadLocal<Write> WRITE = new ThreadLocal<>();

    private EmmaCooldowns() { }

    public static int tick(ServerPlayerEntity player) {
        return ((ItemCooldownManagerAccessor) player.getItemCooldownManager()).sparkwitch$getTick();
    }

    public static int start(ServerPlayerEntity player, Item item) {
        Object entry = ((ItemCooldownManagerAccessor) player.getItemCooldownManager()).sparkwitch$getEntries().get(item);
        return entry instanceof ItemCooldownEntryAccessor value ? value.sparkwitch$getStartTick() : tick(player);
    }

    public static int end(ServerPlayerEntity player, Item item) {
        Object entry = ((ItemCooldownManagerAccessor) player.getItemCooldownManager()).sparkwitch$getEntries().get(item);
        return entry instanceof ItemCooldownEntryAccessor value ? value.sparkwitch$getEndTick() : tick(player);
    }

    public static void setExact(ServerPlayerEntity player, Item item, int ticks) {
        if (SparkTraitsGunBridge.setExactCooldown(player, item, ticks)) return;
        ItemCooldownManager manager = player.getItemCooldownManager();
        Write previous = WRITE.get();
        WRITE.set(new Write(manager, item, Math.max(0, ticks)));
        try {
            manager.set(item, Math.max(0, ticks));
        } finally {
            if (previous == null) WRITE.remove(); else WRITE.set(previous);
        }
    }

    public static Integer exactDuration(ItemCooldownManager manager, Item item) {
        Write write = WRITE.get();
        return write != null && write.manager() == manager && write.item() == item ? write.ticks() : null;
    }

    private record Write(ItemCooldownManager manager, Item item, int ticks) { }
}
