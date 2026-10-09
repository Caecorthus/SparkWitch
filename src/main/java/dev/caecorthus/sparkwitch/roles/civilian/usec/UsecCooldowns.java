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
 * bypasses Fast Hands and the vanilla set runs only when it returns false. No other USEC code writes the rifle
 * cooldown; locks from outside USEC (SparkFactionAPI forced locks such as the Shriek Gun and the AC shell, Saint Karma,
 * the Fiend aura) still can, which is why {@link Status} needs evidence for a bolt.
 * 冻结契约：{@code usec_rifle} 物品冷却（开局锁定与拉栓）“取大 + 精确”的唯一写入方。绝不缩短正在运行的更长冷却
 * （开局、圣徒业报、魔人光环）；SparkTraits 精确写入可绕过快手，仅当其返回 false 时才执行原版写入。USEC 的其他代码不写
 * 狙击枪冷却；USEC 之外的锁定（SparkFactionAPI 强制锁定如尖啸枪与 AC 炮弹、圣徒业报、魔人光环）仍可能写入，因此
 * {@link Status} 需要证据才能判定拉栓。
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
     * Remaining ticks on the rifle item cooldown; 0 when none. Side-neutral (the server's floor write reads it).
     * 狙击枪物品冷却剩余刻数；无冷却时为 0。两端通用（服务端取大写入读取它）。
     */
    public static int remainingTicks(PlayerEntity player) {
        Reading reading = read(player);
        return reading == null ? 0 : reading.status(false).remaining();
    }

    /**
     * One raw read of the rifle cooldown on the player's own cooldown clock, or null when unreadable. Side-neutral:
     * the client reads its synced entry (vanilla's {@code CooldownUpdateS2CPacket} recreates it with the same length,
     * starting at the client tick the packet was handled). Whether the entry is a bolt cycle is not in the entry: only
     * the client's {@code UsecBoltWatch} decides that, from evidence.
     * 在玩家自己的冷却时钟上对步枪冷却的一次原始读取；无法读取时为 null。两端通用：客户端读取已同步的条目（原版
     * {@code CooldownUpdateS2CPacket} 以相同时长重建该条目，起点为处理该数据包时的客户端刻）。条目本身不包含“是否为拉栓”，
     * 只由客户端的 {@code UsecBoltWatch} 依据证据判断。
     */
    public static @Nullable Reading read(@Nullable PlayerEntity player) {
        if (player == null) {
            return null;
        }
        ItemCooldownManager manager = player.getItemCooldownManager();
        if (!(manager instanceof ItemCooldownManagerAccessor accessor)) {
            return null;
        }
        Object entry = accessor.sparkwitch$getEntries().get(SparkWitchItems.usecRifle());
        if (!(entry instanceof ItemCooldownEntryAccessor cooldown)) {
            return new Reading(accessor.sparkwitch$getTick(), Reading.NO_ENTRY, Reading.NO_ENTRY);
        }
        return new Reading(accessor.sparkwitch$getTick(), cooldown.sparkwitch$getStartTick(),
                cooldown.sparkwitch$getEndTick());
    }

    /**
     * Shared pure classifier (HUD, attachment screen, bolt sway): remaining and total ticks from the entry's exact
     * start and end ticks, never from the interpolated, flickering {@code getCooldownProgress}. {@code boltCycle} is
     * the caller's evidence that this entry is a bolt cycle; the length alone never is (a forced lock can be as short
     * as a bolt, see {@link Status}).
     * 共用纯分类器（HUD、配件界面、拉栓晃动）：剩余与总刻数取自条目精确的开始与结束刻，而不是插值后会闪烁的
     * {@code getCooldownProgress}。{@code boltCycle} 是调用方证明该条目为拉栓的证据；仅凭时长永远不算（强制锁定可以和拉栓
     * 一样短，见 {@link Status}）。
     */
    public static Status classify(int startTick, int endTick, int currentTick, boolean boltCycle) {
        int remaining = endTick - currentTick;
        if (remaining <= 0) {
            return Status.NONE;
        }
        return new Status(remaining, Math.max(remaining, endTick - startTick), boltCycle);
    }

    /**
     * One raw read: the cooldown manager's own tick and the rifle entry's start and end ticks ({@link #NO_ENTRY} for
     * both when there is no entry). All three share the manager's clock.
     * 一次原始读取：冷却管理器自身的刻，以及步枪条目的开始与结束刻（没有条目时两者均为 {@link #NO_ENTRY}）。三者共用该管理器的时钟。
     */
    public record Reading(int tick, int startTick, int endTick) {
        public static final int NO_ENTRY = Integer.MIN_VALUE;

        public boolean hasEntry() {
            return startTick != NO_ENTRY && endTick != NO_ENTRY;
        }

        public Status status(boolean boltCycle) {
            return hasEntry() ? classify(startTick, endTick, tick, boltCycle) : Status.NONE;
        }
    }

    /**
     * One rifle cooldown: {@code remaining} and {@code total} ticks (both 0 when none), and whether it is a bolt cycle.
     * {@code bolt} holds only with the caller's evidence and a total of at most {@link UsecRules#BOLT_TICKS} (40, Fast
     * Reload's 28 included). Everything else is another lock for its whole length: the 60 s round start, Saint Karma,
     * the Fiend aura, and also a short SparkFactionAPI forced lock (the Shriek Gun's 40 ticks, the AC shell's up to 8),
     * whose exact write restarts the entry, so its total can equal a bolt's.
     * 一次步枪冷却：{@code remaining} 与 {@code total} 刻（无冷却时均为 0），以及是否为拉栓。只有调用方给出证据且总长不超过
     * {@link UsecRules#BOLT_TICKS}（40，含快速装填的 28）时 {@code bolt} 才成立。其余都是在整个时长内的其他锁定：开局 60 秒、
     * 圣徒业报、魔人光环，以及较短的 SparkFactionAPI 强制锁定（尖啸枪的 40 刻、AC 炮弹最多 8 刻）；其精确写入会重置条目起点，
     * 因此总长可能与拉栓相同。
     */
    public record Status(int remaining, int total, boolean bolt) {
        public static final Status NONE = new Status(0, 0, false);

        public Status {
            bolt = bolt && remaining > 0 && total <= UsecRules.BOLT_TICKS;
        }

        public boolean coolingDown() {
            return remaining > 0;
        }

        public boolean otherLock() {
            return coolingDown() && !bolt;
        }
    }
}
