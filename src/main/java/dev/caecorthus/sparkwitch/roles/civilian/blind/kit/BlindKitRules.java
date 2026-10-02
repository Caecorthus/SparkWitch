package dev.caecorthus.sparkwitch.roles.civilian.blind.kit;

import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindRules;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import org.jetbrains.annotations.Nullable;

/**
 * Pure decisions of the Blind's kit (cane, Attune, loadout, shop landing, money). No world access, so every rule is
 * unit-tested; the services gather the facts and apply the result. Cooldowns count from the end of the active window
 * (C2), so a "ready" tick always includes the window.
 * 盲人道具的纯判定（盲杖、凝神、装备、商店落点、金币）。不访问世界，因此每条规则都可单元测试；由各服务收集事实并执行结果。
 * 冷却从持续窗口结束后开始计算（C2），因此“就绪”刻总是包含持续窗口。
 */
public final class BlindKitRules {
    /** Cadence of the stray-kit sweep for everyone who may not hold it. / 对无权持有者的残留清理间隔。 */
    public static final int STRAY_SWEEP_INTERVAL_TICKS = 20;
    /** While a cane window lasts, players entering the 5-block radius are added this often. / 盲杖窗口内新进入 5 格者的补发间隔。 */
    public static final int CANE_RESCAN_TICKS = 10;
    /** Hotbar cooldown written at a cane tap: the window plus the cooldown after it (C2). / 敲击时写入的物品冷却：窗口加其后冷却。 */
    public static final int CANE_USE_ITEM_COOLDOWN_TICKS =
            BlindRules.CANE_ACTIVE_TICKS + BlindRules.CANE_COOLDOWN_TICKS;
    /** PlayerInventory index of the head equipment slot (39). / 头部装备槽在 PlayerInventory 中的下标（39）。 */
    public static final int HEAD_SLOT = PlayerInventory.MAIN_SIZE + EquipmentSlot.HEAD.getEntitySlotId();
    public static final int NO_SLOT = -1;

    private BlindKitRules() {
    }

    /** An active window and the tick the skill is ready again. / 一个持续窗口及技能再次就绪的刻。 */
    public record Window(long activeUntilTick, long readyTick) {
    }

    /** Cane tap at {@code now}: 5 s window, then 10 s cooldown (C2). / 在 now 敲击盲杖：5 秒窗口，之后冷却 10 秒（C2）。 */
    public static Window caneUse(long now) {
        long activeUntil = now + BlindRules.CANE_ACTIVE_TICKS;
        return new Window(activeUntil, activeUntil + BlindRules.CANE_COOLDOWN_TICKS);
    }

    /** Attune at {@code now}: 10 s window, then 45 s cooldown (C2). / 在 now 凝神：10 秒窗口，之后冷却 45 秒（C2）。 */
    public static Window attuneUse(long now) {
        long activeUntil = now + BlindRules.ATTUNE_ACTIVE_TICKS;
        return new Window(activeUntil, activeUntil + BlindRules.ATTUNE_COOLDOWN_TICKS);
    }

    /** Initial cane cooldown for a new Blind (round start or mid-round, C11). / 新盲人的盲杖初始冷却（开局或中途，C11）。 */
    public static long initialCaneReadyTick(long now) {
        return now + BlindRules.CANE_ROUND_START_COOLDOWN_TICKS;
    }

    /** Initial Attune cooldown for a new Blind (round start or mid-round, C11). / 新盲人的凝神初始冷却。 */
    public static long initialAttuneReadyTick(long now) {
        return now + BlindRules.ATTUNE_ROUND_START_COOLDOWN_TICKS;
    }

    /**
     * Whether the kit state already belongs to this match. Every grant writes a positive cane-ready tick and every
     * clear zeroes it, so {@code caneReadyTick > 0} marks "granted"; the bound match must also equal the current one,
     * which rejects state left from another match. Same-match re-assignments therefore keep their cooldowns.
     * 道具状态是否已属于本局。每次发放都写入正的盲杖就绪刻，每次清除都归零，因此 {@code caneReadyTick > 0} 表示“已发放”；
     * 绑定的对局还必须等于当前对局，以拒绝其他对局遗留的状态。因此同局重复分配会保留冷却。
     */
    public static boolean continuesRound(long caneReadyTick, @Nullable UUID boundMatch, @Nullable UUID currentMatch) {
        return caneReadyTick > 0L && Objects.equals(boundMatch, currentMatch);
    }

    /**
     * Whether a player who is not an active Blind keeps the kit through the sweep: the round is running, the real role
     * is still the Blind, and the player is either playing and alive or inside a SparkTraits-intercepted (Last Stand)
     * death, which may still revive them. Everyone else is stripped.
     * 非激活盲人的玩家在清理中是否保留道具：对局运行中、真实职业仍为盲人，且玩家仍在局存活，或处于 SparkTraits 拦截的
     * （背水一战）死亡中，之后仍可能复活。其他人一律清除。
     */
    public static boolean keepsKit(boolean running, boolean realBlind, boolean playingAndAlive,
                                   boolean deathIntercepted) {
        return running && realBlind && (playingAndAlive || deathIntercepted);
    }

    /**
     * Gate of a cane tap; the vanilla item cooldown is checked by vanilla before {@code use}. A swallowed Blind
     * perceives nothing (C7) and a Blind in psycho mode holds only the bat, so both are refused for free.
     * 敲击盲杖的门槛；原版物品冷却在 {@code use} 之前由原版检查。被吞下的盲人什么也感知不到（C7），疯魔模式中的盲人只持有
     * 球棒，因此二者都被免费拒绝。
     */
    public static boolean canUseCane(boolean activeBlind, boolean roundActive, boolean granted, boolean swallowed,
                                     boolean psycho, boolean stunned, boolean caneReady) {
        return activeBlind && roundActive && granted && !swallowed && !psycho && !stunned && caneReady;
    }

    /** Outcome of an Attune request. / 凝神请求的结果。 */
    public enum AttuneVerdict {
        /** Start Attune. / 开始凝神。 */
        USE,
        /** Silently refused (not a usable Blind, swallowed, stunned, blocked or not ready). / 静默拒绝。 */
        REFUSE,
        /** Refused by Grand Witch Fear; the player is told why. / 被大魔女恐惧拒绝，并提示原因。 */
        FEARED
    }

    /**
     * Attune gate, checked in order: a usable Blind in an ACTIVE round whose kit is granted, not swallowed by a Taotie
     * (the swallowed Blind sees only black, C7), not stunned (Control Expert), not role-skill blocked (SparkTraits),
     * ready; Fear is checked last so its message only appears when Attune would otherwise start.
     * 凝神门槛，按顺序检查：处于 ACTIVE 回合、道具已发放的可用盲人，未被饕餮吞下（被吞的盲人只看到全黑，C7），
     * 未被电击眩晕（控场专家），未被职业技能封锁（SparkTraits），已就绪；恐惧最后检查，使其提示只在本可开始凝神时出现。
     */
    public static AttuneVerdict attuneVerdict(boolean activeBlind, boolean roundActive, boolean granted,
                                              boolean swallowed, boolean stunned, boolean roleSkillBlocked,
                                              boolean attuneReady, boolean feared) {
        if (!activeBlind || !roundActive || !granted || swallowed || stunned || roleSkillBlocked || !attuneReady) {
            return AttuneVerdict.REFUSE;
        }
        return feared ? AttuneVerdict.FEARED : AttuneVerdict.USE;
    }

    /**
     * Whether another player is revealed by the cane: playing and alive, not spectating, not an active Wraith, not
     * swallowed, within {@link BlindRules#CANE_PLAYER_RADIUS}. Invisibility is deliberately ignored (D4).
     * 其他玩家是否被盲杖显示：在局存活、非旁观、非激活的怨灵、未被吞下、且在 {@link BlindRules#CANE_PLAYER_RADIUS} 格内。
     * 刻意忽略隐身（D4）。
     */
    public static boolean isCaneTarget(boolean self, boolean playingAndAlive, boolean spectator, boolean wraith,
                                       boolean swallowed, double squaredDistance) {
        double radius = BlindRules.CANE_PLAYER_RADIUS;
        return !self && playingAndAlive && !spectator && !wraith && !swallowed && squaredDistance <= radius * radius;
    }

    /**
     * Whether this tick re-scans the cane radius: inside the window, every {@link #CANE_RESCAN_TICKS} after the tap
     * (the tap itself already sent the first list).
     * 本刻是否重新扫描盲杖半径：在窗口内、敲击后每隔 {@link #CANE_RESCAN_TICKS} 刻一次（敲击本身已发送第一份名单）。
     */
    public static boolean caneRescanDue(long now, long caneActiveUntilTick) {
        long remaining = caneActiveUntilTick - now;
        return remaining > 0L && remaining < BlindRules.CANE_ACTIVE_TICKS && remaining % CANE_RESCAN_TICKS == 0L;
    }

    /** Staggered per-player sweep tick. / 按玩家错开的清理刻。 */
    public static boolean straySweepDue(long time, int entityId) {
        return Math.floorMod(time + entityId, STRAY_SWEEP_INTERVAL_TICKS) == 0;
    }

    /**
     * Ids of {@code candidates} not revealed yet in this window, in order, capped at the payload limit.
     * 本窗口尚未显示过的候选 id（保持顺序），不超过载荷上限。
     */
    public static int[] entrants(int[] candidates, IntPredicate alreadyRevealed, int limit) {
        int[] out = new int[Math.min(candidates.length, Math.max(0, limit))];
        int count = 0;
        for (int id : candidates) {
            if (count == out.length) {
                break;
            }
            if (!alreadyRevealed.test(id)) {
                out[count++] = id;
            }
        }
        return count == out.length ? out : Arrays.copyOf(out, count);
    }

    /**
     * Hotbar index for a new cane: the selected slot when empty, else the leftmost empty hotbar slot, else -1
     * (Time Stealer rule, duplicated rather than shared).
     * 新盲杖的快捷栏下标：选中栏位为空时优先，否则为最左侧空快捷栏位，否则为 -1（复制而非共享窃时者规则）。
     */
    public static int hotbarTarget(int selectedSlot, IntPredicate emptyHotbarSlot) {
        if (PlayerInventory.isValidHotbarIndex(selectedSlot) && emptyHotbarSlot.test(selectedSlot)) {
            return selectedSlot;
        }
        return firstEmptyHotbarSlot(emptyHotbarSlot);
    }

    /**
     * With a full hotbar, the slot whose item moves out for the cane: the rightmost slot that is not selected, so the
     * held item is never displaced.
     * 快捷栏已满时为盲杖腾出的栏位：最右侧且非选中的栏位，因此从不移走手持物品。
     */
    public static int displacedHotbarSlot(int selectedSlot) {
        int last = PlayerInventory.getHotbarSize() - 1;
        return selectedSlot == last ? last - 1 : last;
    }

    /**
     * Where a bought ComTac lands (C14): the head slot when it is empty, else the leftmost empty hotbar slot (Wathe's
     * default purchase rule), else nowhere (the purchase fails and charges nothing).
     * 购得的 ComTac 落点（C14）：头部槽为空则戴上，否则放入最左侧空快捷栏位（Wathe 默认购买规则），否则无处可放
     * （购买失败且不扣费）。
     */
    public static int comTacLanding(boolean headEmpty, IntPredicate emptyHotbarSlot) {
        return headEmpty ? HEAD_SLOT : firstEmptyHotbarSlot(emptyHotbarSlot);
    }

    /**
     * C14, one per round: a ComTac purchase is refused while the buyer holds one anywhere or already bought one in the
     * current match (a role gained mid-round has no Wathe stock limit, and a stripped ComTac must not be bought again).
     * Without a match record ({@code currentMatch} null) only ownership counts, so no mark outlives its round.
     * C14 每局一件：买家任意位置已持有 ComTac，或在当前对局已买过一件时拒绝购买（回合中途获得的职业不受 Wathe 库存限制，
     * 被清除的 ComTac 也不能再次购买）。没有对局记录（{@code currentMatch} 为 null）时只看是否持有，因此记录不会跨局残留。
     */
    public static boolean refusesComTacPurchase(boolean ownsComTac, @Nullable UUID purchaseMatch,
                                                @Nullable UUID currentMatch) {
        return ownsComTac || (currentMatch != null && currentMatch.equals(purchaseMatch));
    }

    /**
     * Raise-only cooldown write: nothing unless {@code requested} is longer than what already runs (a forced cooldown,
     * e.g. the Fiend aura, is never shortened); then exactly one writer runs, SparkTraits' exact write or, only when it
     * reports false, the vanilla set.
     * 只增不减的冷却写入：仅当 requested 长于当前剩余时才写入（强制冷却如魔人光环永不被缩短）；随后恰好执行一个写入方：
     * SparkTraits 精确写入，或仅当其返回 false 时的原版写入。
     */
    public static boolean writeRaisedCooldown(int existingTicks, int requestedTicks, IntPredicate exactWriter,
                                              IntConsumer vanillaWriter) {
        int existing = Math.max(0, existingTicks);
        if (requestedTicks <= existing) {
            return false;
        }
        if (!exactWriter.test(requestedTicks)) {
            vanillaWriter.accept(requestedTicks);
        }
        return true;
    }

    /** Angler rule: a living participant Blind sees its balance; otherwise no opinion. / 钓鱼佬规则：存活参与的盲人可见余额。 */
    public static @Nullable CanSeeMoney.Result moneyVisibility(boolean livingParticipant, boolean blind) {
        return livingParticipant && blind ? CanSeeMoney.Result.ALLOW : null;
    }

    private static int firstEmptyHotbarSlot(IntPredicate emptyHotbarSlot) {
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            if (emptyHotbarSlot.test(slot)) {
                return slot;
            }
        }
        return NO_SLOT;
    }
}
