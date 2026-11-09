package dev.caecorthus.sparkwitch.roles.civilian.savior;

import dev.caecorthus.sparkfactionapi.api.cooldown.CooldownKind;
import dev.caecorthus.sparkfactionapi.api.cooldown.CooldownSlot;
import dev.caecorthus.sparkfactionapi.api.cooldown.ForcedCooldowns;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownEntryAccessor;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownManagerAccessor;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The single writer of the Savior's own weapon cooldowns, and the observed-kill halving. Server thread only.
 * {@link #writeCooldown} is raise-only: it never shortens a longer lock already on the weapon (Knight duel seal, Abyss
 * suppression, Saint Karma, any SparkFactionAPI forced cooldown); the natural write goes through vanilla {@code set},
 * so SparkTraits modifiers (Fast Hands, ...) shape the Savior's own cooldown, and its effective length and end tick are
 * remembered. {@link #reduceAfterObservedKill} shortens only that own part, by half of its effective length, and only
 * while the live cooldown is still the Savior's own entry (same end tick); it never goes below a forced remainder:
 * {@code ForcedCooldowns.clearItemKeepingForced} keeps what was forced, and SparkTraits' exact setter writes the rest so
 * modifiers are not applied twice. Remaining time is read from SparkFactionAPI, not SparkTraits.
 * 救世主自身武器冷却的唯一写入方，以及目击击杀后的减半。仅限服务端线程。{@link #writeCooldown} 只抬高：绝不缩短武器上已有的
 * 更长锁定（骑士决斗封印、聆渊者压制、圣徒业报、任何 SparkFactionAPI 强制冷却）；自然写入经由原版 {@code set}，使
 * SparkTraits 倍率（快手等）作用于救世主自身冷却，并记住其实际时长与结束刻。{@link #reduceAfterObservedKill} 只缩短这部分
 * 自身冷却，减去其实际时长的一半，且仅当实时冷却仍是救世主自己的条目（结束刻相同）时才缩短；绝不低于强制剩余：
 * {@code ForcedCooldowns.clearItemKeepingForced} 保留强制部分，其余由 SparkTraits 精确写入，避免重复计算倍率。剩余时间
 * 读取自 SparkFactionAPI，而非 SparkTraits。
 */
public final class SaviorCooldowns {
    /**
     * Player UUID → weapon → the Savior's own cooldown part. Server thread only; cleared per player on death, reset and
     * disconnect ({@link #clear}) and wholesale at round finalize and server stop ({@link #forgetAll}).
     * 玩家→武器→救世主自身冷却部分。仅服务端线程；死亡、重置与断线时逐个清除（{@link #clear}），回合收尾与服务器停止时整体清空
     * （{@link #forgetAll}）。
     */
    private static final Map<UUID, Map<Item, OwnCooldown>> OWN = new HashMap<>();

    private SaviorCooldowns() {
    }

    /**
     * The Savior's own cooldown on one weapon: its end tick on the player's own item-cooldown clock and its effective
     * length (after SparkTraits modifiers). / 救世主在一把武器上的自身冷却：玩家物品冷却时钟上的结束刻，以及实际时长（经
     * SparkTraits 倍率后）。
     */
    record OwnCooldown(int endTick, int duration) {
    }

    /**
     * Raise-only write of the Savior's own cooldown, remembering its effective length. Every Savior weapon cooldown goes
     * through this one writer; the post-use values are the SFA nominals in {@code SparkWitchItemCooldownNominals}.
     * 只抬高地写入救世主自身冷却，并记住其实际时长。救世主武器冷却只经由此写入；使用后数值即
     * {@code SparkWitchItemCooldownNominals} 中的 SFA 标准冷却。
     */
    public static void writeCooldown(ServerPlayerEntity player, Item item, int ticks) {
        if (player == null || item == null || ticks <= 0) {
            return;
        }
        int existing = ForcedCooldowns.itemRemainingTicks(player, item);
        int own;
        if (ticks > existing) {
            // Natural write: SparkTraits modifiers apply. / 自然写入：SparkTraits 倍率生效。
            player.getItemCooldownManager().set(item, ticks);
            own = ForcedCooldowns.itemRemainingTicks(player, item);
            if (own < existing) {
                // A modifier pulled it under the running lock: put that lock back exactly.
                // 倍率把它压到了正在运行的锁定之下：精确恢复该锁定。
                writeExact(player, item, existing);
            }
        } else {
            // A longer lock already runs and is kept; the own part is at most the nominal.
            // 已有更长的锁定在运行并予以保留；自身部分至多为标称值。
            own = ticks;
        }
        Integer tick = cooldownTick(player);
        if (tick == null || own <= 0) {
            forget(player, item);
            return;
        }
        OWN.computeIfAbsent(player.getUuid(), ignored -> new HashMap<>()).put(item, new OwnCooldown(tick + own, own));
    }

    /**
     * Shortens both weapons' own cooldowns by half of their effective length; weapons not cooling are ignored.
     * 两把武器的自身冷却各减去其实际时长的一半；未冷却的忽略。
     */
    public static void reduceAfterObservedKill(ServerPlayerEntity savior) {
        Map<Item, OwnCooldown> entries = OWN.get(savior.getUuid());
        if (entries == null) {
            return;
        }
        reduce(savior, SparkWitchItems.saviorPistol(), entries);
        reduce(savior, SparkWitchItems.saviorLightsaber(), entries);
    }

    private static void reduce(ServerPlayerEntity savior, Item item, Map<Item, OwnCooldown> entries) {
        OwnCooldown own = entries.get(item);
        Integer tick = cooldownTick(savior);
        if (own == null || tick == null) {
            return;
        }
        int ownRemaining = own.endTick() - tick;
        if (ownRemaining <= 0) {
            entries.remove(item);
            return;
        }
        int ownNext = reducedOwnRemaining(ownRemaining, own.duration());
        boolean ours = shortensLiveCooldown(liveEndTick(savior, item), own.endTick());
        entries.put(item, new OwnCooldown(tick + ownNext, own.duration()));
        if (!ours) {
            // The live cooldown is a longer lock from elsewhere (or no longer ours): only the own record shrinks.
            // 实时冷却是来自他处的更长锁定（或已不属于自身）：只缩短自身记录。
            return;
        }
        // Forced floors covered by the own part survive the clear and are re-installed exactly.
        // 被自身部分覆盖的强制下限在清除后保留并被精确重装。
        int kept = ForcedCooldowns.clearItemKeepingForced(savior, item);
        if (ownNext <= kept) {
            return;
        }
        writeExact(savior, item, ownNext);
        if (kept > 0) {
            // The exact write replaced the entry: record the kept forced floor on it again (no write, it is covered).
            // 精确写入替换了条目：在新条目上重新记录保留的强制下限（已被覆盖，不会写入）。
            ForcedCooldowns.raise(savior, new CooldownSlot(CooldownKind.ITEM, Registries.ITEM.getId(item), ownNext,
                    ForcedCooldowns.itemNominalTicks(item)), kept);
        }
        Integer liveEnd = liveEndTick(savior, item);
        if (liveEnd != null) {
            entries.put(item, new OwnCooldown(liveEnd, own.duration()));
        }
    }

    /**
     * Pure: the own remainder after one observed kill, {@code remaining - duration / 2}, floored at 0.
     * 纯函数：一次目击击杀后的自身剩余，{@code 剩余 - 时长 / 2}，下限为 0。
     */
    static int reducedOwnRemaining(int ownRemaining, int duration) {
        return Math.max(0, ownRemaining - Math.max(0, duration) / 2);
    }

    /**
     * Pure: the live cooldown may be shortened only while it is the Savior's own entry, i.e. it ends exactly where the
     * remembered own part ends; a later, longer lock (or a replacement) ends elsewhere.
     * 纯函数：只有实时冷却仍是救世主自己的条目（结束刻与记录的自身部分完全相同）时才可缩短；之后的更长锁定（或替换）结束于别处。
     */
    static boolean shortensLiveCooldown(@Nullable Integer liveEndTick, int ownEndTick) {
        return liveEndTick != null && liveEndTick == ownEndTick;
    }

    /** SparkTraits' exact write first (past Fast Hands), the vanilla set only when it answers false. / 先精确写入，失败才原版写入。 */
    private static void writeExact(ServerPlayerEntity player, Item item, int ticks) {
        if (!SparkTraitsKillerBridge.setExactItemCooldownRemaining(player, item, ticks)) {
            player.getItemCooldownManager().set(item, ticks);
        }
    }

    /** The player's own item-cooldown clock, or null when unreadable. / 玩家自身物品冷却时钟；无法读取时为 null。 */
    private static @Nullable Integer cooldownTick(ServerPlayerEntity player) {
        return player.getItemCooldownManager() instanceof ItemCooldownManagerAccessor accessor
                ? accessor.sparkwitch$getTick()
                : null;
    }

    /** End tick of the live cooldown entry, or null when the item is not cooling. / 实时冷却条目的结束刻；未冷却时为 null。 */
    private static @Nullable Integer liveEndTick(ServerPlayerEntity player, Item item) {
        if (!(player.getItemCooldownManager() instanceof ItemCooldownManagerAccessor accessor)) {
            return null;
        }
        return accessor.sparkwitch$getEntries().get(item) instanceof ItemCooldownEntryAccessor entry
                ? entry.sparkwitch$getEndTick()
                : null;
    }

    private static void forget(ServerPlayerEntity player, Item item) {
        Map<Item, OwnCooldown> entries = OWN.get(player.getUuid());
        if (entries != null) {
            entries.remove(item);
        }
    }

    /** Forgets one player's own cooldowns (death, reset, disconnect). / 忘记单名玩家的自身冷却（死亡、重置、断线）。 */
    public static void clear(ServerPlayerEntity player) {
        OWN.remove(player.getUuid());
    }

    /** Forgets everyone (round finalize, server stop). / 忘记所有人（回合收尾、服务器停止）。 */
    static void forgetAll() {
        OWN.clear();
    }
}
