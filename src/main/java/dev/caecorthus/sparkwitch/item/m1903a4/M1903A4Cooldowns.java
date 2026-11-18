package dev.caecorthus.sparkwitch.item.m1903a4;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownEntryAccessor;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownManagerAccessor;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * Frozen contract: the single "max + exact" writer of the {@code m1903a4} item cooldown (round-start lock, shot and
 * reload floor), following {@code UsecCooldowns}. A longer running cooldown (round start, Knight duel seal, Fiend aura,
 * Abyss suppression, AC shell, Saint penalty) is never shortened; SparkTraits' exact write bypasses Fast Hands and the
 * vanilla set runs only when it returns false. The cooldown lives on the holder's cooldown manager only (no per-stack
 * deadline), so the fire gate reads one clock.
 * 冻结契约：{@code m1903a4} 物品冷却（开局锁定、开火与装填下限）“取大 + 精确”的唯一写入方，沿用 {@code UsecCooldowns}。
 * 绝不缩短正在运行的更长冷却（开局、骑士决斗封印、魔人光环、深渊压制、AC 炮弹、圣徒惩罚）；SparkTraits 精确写入可绕过快手，
 * 仅当其返回 false 时才执行原版写入。冷却只存在于持有者的冷却管理器中（没有按物品堆的截止时间），因此开火门槛只读一个时钟。
 */
public final class M1903A4Cooldowns {
    private M1903A4Cooldowns() {
    }

    /** Server only: the 60 s round-start lock. / 仅服务端：开局 60 秒锁定。 */
    public static boolean roundStart(ServerPlayerEntity player) {
        return writeFloorExact(player, M1903A4Rules.ROUND_START_COOLDOWN_TICKS);
    }

    /** Server only: the 10 s after a shot. / 仅服务端：开火后 10 秒。 */
    public static boolean shot(ServerPlayerEntity player) {
        return writeFloorExact(player, M1903A4Rules.SHOT_COOLDOWN_TICKS);
    }

    /** Server only: the 2 s floor after a round is loaded. / 仅服务端：装入一发后的 2 秒下限。 */
    public static boolean reload(ServerPlayerEntity player) {
        return writeFloorExact(player, M1903A4Rules.RELOAD_COOLDOWN_TICKS);
    }

    /**
     * Server only. Writes {@code max(existing, ticks)} exactly once; returns true when a new value was written.
     * 仅服务端。恰好写入一次 {@code max(既有, ticks)}；写入了新值时返回 true。
     */
    public static boolean writeFloorExact(ServerPlayerEntity player, int ticks) {
        if (player == null || ticks <= 0) {
            return false;
        }
        Item rifle = SparkWitchItems.m1903a4();
        return write(remainingTicks(player), ticks,
                merged -> SparkTraitsKillerBridge.setExactItemCooldownRemaining(player, rifle, merged),
                merged -> player.getItemCooldownManager().set(rifle, merged));
    }

    /** Pure write rule (unit-testable). / 纯写入规则（可单测）。 */
    static boolean write(int existingTicks, int requestedTicks, IntPredicate exactWriter, IntConsumer vanillaWriter) {
        int existing = Math.max(0, existingTicks);
        int merged = Math.max(existing, Math.max(0, requestedTicks));
        if (merged <= existing) {
            return false;
        }
        if (!exactWriter.test(merged)) {
            vanillaWriter.accept(merged);
        }
        return true;
    }

    /**
     * Remaining ticks on the rifle item cooldown on the player's own clock; 0 when none.
     * 玩家自身时钟上步枪物品冷却的剩余刻数；无冷却时为 0。
     */
    public static int remainingTicks(PlayerEntity player) {
        if (player == null || !(player.getItemCooldownManager() instanceof ItemCooldownManagerAccessor accessor)) {
            return 0;
        }
        Object entry = accessor.sparkwitch$getEntries().get(SparkWitchItems.m1903a4());
        if (!(entry instanceof ItemCooldownEntryAccessor cooldown)) {
            return 0;
        }
        return Math.max(0, cooldown.sparkwitch$getEndTick() - accessor.sparkwitch$getTick());
    }
}
