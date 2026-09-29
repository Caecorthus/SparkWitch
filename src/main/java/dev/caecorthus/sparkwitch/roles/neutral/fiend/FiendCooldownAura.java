package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownEntryAccessor;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownManagerAccessor;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * Gun-hit cooldown aura (C4). Queued at the hit and applied at {@code END_SERVER_TICK}, after the gun handler wrote the
 * shooter's own post-shot cooldown, so that write cannot undercut the floor. Every other playing, alive, non-spectator
 * participant within {@link FiendRules#AURA_RADIUS} of the Fiend (the shooter included) gets every distinct carried item
 * (main, offhand, armor) raised to at least {@link FiendRules#AURA_COOLDOWN_TICKS}; nothing is ever shortened.
 * NoellesRoles' timed bomb is skipped (C15): its item cooldown is the Bomber pass gate, and a 20 s floor would outlast
 * the 15 s beep and always kill the holder. Role-skill counters are untouched. Server only.
 * 枪击冷却光环（C4）。在受击时入队，于 {@code END_SERVER_TICK} 结算，此时枪械处理器已写入开枪者自己的射击冷却，不会压低
 * 下限。魔人 {@link FiendRules#AURA_RADIUS} 格内其他在局、存活、非旁观的参与者（含开枪者）身上每种物品（主背包、副手、
 * 盔甲）的冷却提升至至少 {@link FiendRules#AURA_COOLDOWN_TICKS}，绝不缩短。NoellesRoles 定时炸弹除外（C15）：其物品冷却
 * 是炸弹客的转手门槛，20 秒下限会超过 15 秒的蜂鸣期，必然炸死持有者。职业技能计数不受影响。仅服务端。
 */
public final class FiendCooldownAura {
    static final Identifier TIMED_BOMB_ID = Identifier.of("noellesroles", "timed_bomb");
    private static final Set<UUID> PENDING = new LinkedHashSet<>();
    private static boolean registered;

    private FiendCooldownAura() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(FiendCooldownAura::flush);
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> PENDING.clear());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
    }

    static void enqueue(ServerPlayerEntity fiend) {
        PENDING.add(fiend.getUuid());
    }

    private static void flush(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        List<UUID> fiends = List.copyOf(PENDING);
        PENDING.clear();
        for (UUID id : fiends) {
            ServerPlayerEntity fiend = server.getPlayerManager().getPlayer(id);
            if (fiend != null) {
                pulse(fiend);
            }
        }
    }

    private static void pulse(ServerPlayerEntity fiend) {
        for (ServerPlayerEntity player : List.copyOf(fiend.getServerWorld().getPlayers())) {
            if (isTarget(player == fiend, GameFunctions.isPlayerPlayingAndAlive(player), player.isSpectator(),
                    player.squaredDistanceTo(fiend))) {
                raiseCarriedItems(player);
            }
        }
    }

    /** Pure target filter; swallowed players are living spectators and are skipped. / 纯目标过滤；被吞者是存活旁观者，被跳过。 */
    static boolean isTarget(boolean isFiend, boolean playingAndAlive, boolean spectator, double squaredDistance) {
        return !isFiend && playingAndAlive && !spectator
                && squaredDistance <= FiendRules.AURA_RADIUS * FiendRules.AURA_RADIUS;
    }

    private static void raiseCarriedItems(ServerPlayerEntity player) {
        Set<Item> items = new LinkedHashSet<>();
        collect(player.getInventory().main, items);
        collect(player.getInventory().offHand, items);
        collect(player.getInventory().armor, items);
        ItemCooldownManager manager = player.getItemCooldownManager();
        for (Item item : items) {
            write(remainingTicks(manager, item),
                    ticks -> SparkTraitsKillerBridge.setExactItemCooldownRemaining(player, item, ticks),
                    ticks -> manager.set(item, ticks));
        }
    }

    /**
     * Pure floor write: nothing unless the remaining time is below the floor; then exactly one writer runs, the
     * SparkTraits exact write (past cooldown modifiers) or, only when it reports false, the vanilla set.
     * 纯下限写入：剩余时间不低于下限时不写；否则恰好执行一个写入方：SparkTraits 精确写入（越过冷却倍率），仅当其返回
     * false 时才用原版写入。
     */
    static boolean write(int remainingTicks, IntPredicate exactWriter, IntConsumer vanillaWriter) {
        int existing = Math.max(0, remainingTicks);
        int raised = FiendRules.raisedCooldown(existing);
        if (raised <= existing) {
            return false;
        }
        if (!exactWriter.test(raised)) {
            vanillaWriter.accept(raised);
        }
        return true;
    }

    /** Pure item filter: every carried item except the timed bomb. / 纯物品过滤：除定时炸弹外的所有携带物品。 */
    static boolean isRaised(Identifier itemId) {
        return !TIMED_BOMB_ID.equals(itemId);
    }

    private static void collect(Iterable<ItemStack> stacks, Set<Item> items) {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && isRaised(Registries.ITEM.getId(stack.getItem()))) {
                items.add(stack.getItem());
            }
        }
    }

    private static int remainingTicks(ItemCooldownManager manager, Item item) {
        if (!(manager instanceof ItemCooldownManagerAccessor accessor)
                || !(accessor.sparkwitch$getEntries().get(item) instanceof ItemCooldownEntryAccessor entry)) {
            return 0;
        }
        return Math.max(0, entry.sparkwitch$getEndTick() - accessor.sparkwitch$getTick());
    }
}
