package dev.caecorthus.sparkwitch.roles.civilian.saint;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownEntryAccessor;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownManagerAccessor;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Applies Karma to item types currently carried in main inventory, hotbar, or offhand; armor is excluded.
 * 将业障冷却施加到主背包、快捷栏和副手内的物品类型；盔甲栏不在范围内。
 */
public final class SaintKarmaCooldownService {
    private SaintKarmaCooldownService() {
    }

    public static void apply(ServerPlayerEntity player, int requestedTicks) {
        if (requestedTicks <= 0) {
            return;
        }

        Set<Item> carriedItems = new HashSet<>();
        collectItems(player.getInventory().main, carriedItems);
        collectItems(player.getInventory().offHand, carriedItems);

        WitchWorldComponent worldComponent = WitchWorldComponent.KEY.get(player.getServerWorld());
        ItemCooldownManager manager = player.getItemCooldownManager();
        ItemCooldownManagerAccessor managerAccessor = (ItemCooldownManagerAccessor) manager;
        Map<Item, ?> entries = managerAccessor.sparkwitch$getEntries();
        int currentTick = managerAccessor.sparkwitch$getTick();
        for (Item item : carriedItems) {
            if (worldComponent.isSaintKarmaAdminCleared(player.getUuid(), Registries.ITEM.getId(item))) {
                continue;
            }
            int existingTicks = remainingTicks(entries.get(item), currentTick);
            int mergedTicks = SaintRules.mergeCooldownTicks(existingTicks, requestedTicks);
            if (mergedTicks > existingTicks) {
                write(player, manager, item, mergedTicks);
            }
        }
    }

    /**
     * An admin clear (SparkFactionAPI {@code clearCooldown}) that really removed {@code item}'s cooldown sticks for the
     * running Karma: Karma stops re-covering that item until it ends or is triggered again.
     * 真正移除了 {@code item} 冷却的管理员清除（SparkFactionAPI {@code clearCooldown}）在本次业障内保持有效：业障结束或
     * 再次触发前不再覆盖该物品。
     */
    public static void exemptAdminCleared(ServerPlayerEntity player, Item item) {
        WitchWorldComponent.KEY.get(player.getServerWorld())
                .exemptSaintKarmaAdminCleared(player.getUuid(), Registries.ITEM.getId(item));
    }

    /**
     * Exact through SparkTraits, vanilla only as the fallback (never both). A shortened write (Fast Hands, NoellesRoles
     * Stimulation) would stay below the Karma and be rewritten every tick, restarting the slot's cooldown bar.
     * 经 SparkTraits 精确写入，仅在其缺失时回退原版写入（二者不会同时执行）。被缩短的写入（快手、NoellesRoles 兴奋剂）
     * 会一直低于业障并每 tick 重写，使栏位冷却条不断从头开始。
     */
    private static void write(ServerPlayerEntity player, ItemCooldownManager manager, Item item, int mergedTicks) {
        if (!SparkTraitsKillerBridge.setExactItemCooldownRemaining(player, item, mergedTicks)) {
            manager.set(item, mergedTicks);
        }
    }

    private static void collectItems(Iterable<ItemStack> stacks, Set<Item> items) {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                items.add(stack.getItem());
            }
        }
    }

    private static int remainingTicks(Object entry, int currentTick) {
        if (!(entry instanceof ItemCooldownEntryAccessor accessor)) {
            return 0;
        }
        return Math.max(0, accessor.sparkwitch$getEndTick() - currentTick);
    }
}
