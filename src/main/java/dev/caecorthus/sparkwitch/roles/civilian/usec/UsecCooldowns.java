package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsUsecBridge;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownEntryAccessor;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownManagerAccessor;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * Frozen contract: the single "max + exact" writer of the {@code usec_rifle} item cooldown (round-start lock and bolt
 * cycle). A longer running cooldown (round start, Saint Karma, Fiend aura) is never shortened; SparkTraits' exact write
 * bypasses Fast Hands and the vanilla set runs only when it returns false. Nothing else writes the rifle cooldown.
 * 冻结契约：{@code usec_rifle} 物品冷却（开局锁定与拉栓）“取大 + 精确”的唯一写入方。绝不缩短正在运行的更长冷却
 * （开局、圣徒业报、魔人光环）；SparkTraits 精确写入可绕过快手，仅当其返回 false 时才执行原版写入。其他地方不写狙击枪冷却。
 */
public final class UsecCooldowns {
    private UsecCooldowns() {
    }

    /** Server only: the 60 s round-start lock. / 仅服务端：开局 60 秒锁定。 */
    public static boolean roundStart(ServerPlayerEntity player) {
        return writeFloorExact(player, UsecRules.ROUND_START_COOLDOWN_TICKS);
    }

    /**
     * Server only: the bolt cycle after a shot, a magazine change into an empty chamber, or a single round chambered.
     * SparkTraits Fast Reload shortens it ×0.7.
     * 仅服务端：开枪后、空膛换弹匣后或单发压膛后的拉栓。SparkTraits 快速装填使其 ×0.7。
     */
    public static boolean bolt(ServerPlayerEntity player) {
        return writeFloorExact(player, boltTicks(SparkTraitsUsecBridge.hasFastReload(player)));
    }

    /** Bolt ticks with or without Fast Reload (40 or 28). / 有无快速装填时的拉栓刻数（40 或 28）。 */
    public static int boltTicks(boolean fastReload) {
        return fastReload
                ? (int) Math.round(UsecRules.BOLT_TICKS * UsecRules.FAST_RELOAD_BOLT_FACTOR)
                : UsecRules.BOLT_TICKS;
    }

    /**
     * Server only. Writes {@code max(existing, ticks)} exactly once; returns true when a new value was written.
     * 仅服务端。恰好写入一次 {@code max(既有, ticks)}；写入了新值时返回 true。
     */
    public static boolean writeFloorExact(ServerPlayerEntity player, int ticks) {
        if (player == null || ticks <= 0) {
            return false;
        }
        Item rifle = SparkWitchItems.usecRifle();
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
     * Remaining ticks on the rifle item cooldown; 0 when none. Side-neutral (the client reads its synced cooldown for
     * the HUD bolt indicator).
     * 狙击枪物品冷却剩余刻数；无冷却时为 0。两端通用（客户端读取已同步的冷却用于 HUD 拉栓指示）。
     */
    public static int remainingTicks(PlayerEntity player) {
        return status(player).remaining();
    }

    /**
     * The rifle cooldown entry as a {@link Status}; {@link Status#NONE} when none. Side-neutral: the client reads its
     * synced entry (vanilla's {@code CooldownUpdateS2CPacket} recreates it with the same length).
     * 狙击枪冷却条目对应的 {@link Status}；无冷却时为 {@link Status#NONE}。两端通用：客户端读取已同步的条目（原版
     * {@code CooldownUpdateS2CPacket} 以相同时长重建该条目）。
     */
    public static Status status(@Nullable PlayerEntity player) {
        if (player == null) {
            return Status.NONE;
        }
        ItemCooldownManager manager = player.getItemCooldownManager();
        if (!(manager instanceof ItemCooldownManagerAccessor accessor)) {
            return Status.NONE;
        }
        Object entry = accessor.sparkwitch$getEntries().get(SparkWitchItems.usecRifle());
        if (!(entry instanceof ItemCooldownEntryAccessor cooldown)) {
            return Status.NONE;
        }
        return classify(cooldown.sparkwitch$getStartTick(), cooldown.sparkwitch$getEndTick(),
                accessor.sparkwitch$getTick());
    }

    /**
     * Shared pure classifier (HUD and attachment screen): remaining and total ticks from the entry's exact start and
     * end ticks, never from the interpolated, flickering {@code getCooldownProgress}.
     * 共用纯分类器（HUD 与配件界面）：剩余与总刻数取自条目精确的开始与结束刻，而不是插值后会闪烁的
     * {@code getCooldownProgress}。
     */
    public static Status classify(int startTick, int endTick, int currentTick) {
        int remaining = endTick - currentTick;
        if (remaining <= 0) {
            return Status.NONE;
        }
        return new Status(remaining, Math.max(remaining, endTick - startTick));
    }

    /**
     * One rifle cooldown: {@code remaining} and {@code total} ticks (both 0 when none). A total of at most
     * {@link UsecRules#BOLT_TICKS} (40, Fast Reload's 28 included) is a bolt cycle; anything longer (the 60 s round
     * start, Saint Karma, the Fiend aura) is another lock, even in its last 40 ticks.
     * 一次步枪冷却：{@code remaining} 与 {@code total} 刻（无冷却时均为 0）。总长不超过 {@link UsecRules#BOLT_TICKS}
     * （40，含快速装填的 28）即为拉栓；更长的（开局 60 秒、圣徒业报、魔人光环）都是其他锁定，即使只剩最后 40 刻也是。
     */
    public record Status(int remaining, int total) {
        public static final Status NONE = new Status(0, 0);

        public boolean coolingDown() {
            return remaining > 0;
        }

        public boolean bolt() {
            return coolingDown() && total <= UsecRules.BOLT_TICKS;
        }

        public boolean otherLock() {
            return coolingDown() && !bolt();
        }
    }
}
