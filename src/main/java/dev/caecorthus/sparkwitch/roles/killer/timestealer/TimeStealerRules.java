package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Stable gameplay values and pure gates owned by the Time Stealer. Every value here is a stable contract shared by the
 * role's services, replay and client; nothing reads Minecraft registries, so the class stays safe to load in unit tests.
 * 窃时者拥有的稳定玩法数值与纯规则判断。这里的每个值都是该职业服务、回放与客户端共享的稳定契约；不读取任何 Minecraft
 * 注册表，因此可以安全地在单元测试中加载。
 */
public final class TimeStealerRules {
    public static final Identifier ROLE_ID = SparkWitch.id("time_stealer");
    public static final Identifier CLOCK_ID = SparkWitch.id("time_stealer_clock");
    public static final Identifier STAMP_ID = SparkWitch.id("time_stamp");
    /** Item-use replay record id for a stamp purchase. / 邮票购买的物品使用回放记录 id。 */
    public static final Identifier STAMP_PURCHASE_RECORD_ID = SparkWitch.id("time_stamp_purchase");
    /**
     * Frozen payload of a {@link #STAMP_PURCHASE_RECORD_ID} item-use record (the stamp purchase writes it, the replay
     * formatter reads it): the Wathe {@code ShopEntry.id()} String under this key. The formatter maps it to a display
     * name through the Time Stealer shop entry ids; unknown ids fall back to the raw String, never to an
     * {@code Identifier} parse.
     * 冻结的邮票购买回放载荷（邮票购买写入，回放格式化器读取）：该键下是 Wathe {@code ShopEntry.id()} 字符串。
     * 格式化器按窃时者商店商品 id 映射显示名；未知 id 直接显示原字符串，绝不按 {@code Identifier} 解析。
     */
    public static final String REPLAY_ENTRY_KEY = "entry";
    /** Frozen payload key: stamps spent, an int. / 冻结的载荷键：花费的邮票数（int）。 */
    public static final String REPLAY_COST_KEY = "cost";
    public static final int COLOR = 0x2E8B7A;

    /** Clock cooldown after a committed theft (45 s). / 成功窃取后的时钟冷却（45 秒）。 */
    public static final int CLOCK_COOLDOWN_TICKS = 45 * 20;
    /** Round-start Clock cooldown (45 s). / 开局时钟冷却（45 秒）。 */
    public static final int CLOCK_INITIAL_COOLDOWN_TICKS = 45 * 20;
    /** Silent period before the first chime (15 s). / 第一声钟响前的静默期（15 秒）。 */
    public static final int GRACE_TICKS = 15 * 20;
    /** Gap between chimes (5 s). / 相邻钟声的间隔（5 秒）。 */
    public static final int STEP_TICKS = 5 * 20;
    /** Owner decision Q1: the fifth chime (35 s) is lethal. / 所有者决定 Q1：第 5 声钟响（35 秒）致死。 */
    public static final int LETHAL_STAGE = 5;
    /** Stage n applies Slowness amplifier n - 1 for this long; reapplied every stage. / 第 n 阶施加放大器 n-1 的缓慢，持续该时长，每阶重新施加。 */
    public static final int SLOWNESS_DURATION_TICKS = 120;
    /** Round time removed after a confirmed Clock kill (30 s). / 确认时钟击杀后扣除的对局时间（30 秒）。 */
    public static final int CLOCK_KILL_TIME_PENALTY_TICKS = 30 * 20;
    /** Owner decision Q8: the penalty may drain the round to exactly 0. / 所有者决定 Q8：扣时可以扣到恰好为 0。 */
    public static final int CLOCK_KILL_TIME_FLOOR_TICKS = 0;
    /** Round time added by the "+1 minute" stamp purchase. / “+1 分钟”邮票商品增加的对局时间。 */
    public static final int STAMP_ADD_TIME_TICKS = 60 * 20;
    public static final int STAMP_MAX_STACK = 64;
    public static final int STAMP_COST_ADD_TIME = 1;
    public static final int STAMP_COST_GRENADE = 3;
    public static final int STAMP_COST_PSYCHO = 3;

    /** Hard Clock reach: ray length and the real eye-to-hitbox distance cap. / 时钟硬射程：射线长度与眼睛到真实命中盒的距离上限。 */
    public static final double CLOCK_RANGE = 7.0D;
    /** Aim tolerance only; never extends the range or the line-of-sight box. / 仅用于瞄准容差，从不延长射程或视线判定盒。 */
    public static final double CLOCK_BOX_EXPANSION = 0.2D;

    private TimeStealerRules() {
    }

    /** Exact-role gate; never inferred from faction or namespace. / 仅按精确职业判断，不从阵营或命名空间推断。 */
    public static boolean isTimeStealer(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    /**
     * Curse stage reached after {@code elapsedTicks} since the theft: 0 during the silent period, then 1..4 at
     * 15/20/25/30 s, and {@link #LETHAL_STAGE} from 35 s on (owner decision Q1).
     * 被窃后经过 {@code elapsedTicks} 时所处的诅咒阶段：静默期为 0，15/20/25/30 秒依次为 1..4，
     * 35 秒起为 {@link #LETHAL_STAGE}（所有者决定 Q1）。
     */
    public static int dueStage(long elapsedTicks) {
        if (elapsedTicks < GRACE_TICKS) {
            return 0;
        }
        long stage = 1L + (elapsedTicks - GRACE_TICKS) / STEP_TICKS;
        return (int) Math.min(LETHAL_STAGE, stage);
    }

    public static boolean isLethal(int stage) {
        return stage >= LETHAL_STAGE;
    }

    /**
     * Round time after a confirmed Clock kill: {@code max(0, c - 600)} (owner decision Q8). It never increases the
     * time, even for an out-of-range negative input, and never overflows.
     * 确认时钟击杀后的对局时间：{@code max(0, c - 600)}（所有者决定 Q8）。即使输入为越界的负数也绝不增加时间，且不会溢出。
     */
    public static int timeAfterClockKill(int currentTicks) {
        long reduced = Math.max(CLOCK_KILL_TIME_FLOOR_TICKS, (long) currentTicks - CLOCK_KILL_TIME_PENALTY_TICKS);
        return (int) Math.min(currentTicks, reduced);
    }

    /**
     * Only the Clock curse death skips Wathe's +30 s civilian-kill time bonus.
     * 只有时钟诅咒死亡会跳过 Wathe 平民死亡时的 +30 秒时间奖励。
     */
    public static boolean suppressesCivilianKillTime(@Nullable Identifier deathReason) {
        return SparkWitchDeathReasons.TIME_STOLEN.equals(deathReason);
    }

    /**
     * Second owner-approved exception after the bell toll: only the forced Clock curse kill passes guards that
     * ignore Wathe's {@code force} flag (e.g. Saint's HEAD guard); every other kill keeps its protection path.
     * 继丧钟之后的第二个所有者批准例外：仅强制的时钟诅咒击杀可穿过忽略 Wathe {@code force} 标记的拦截
     * （如圣徒的 HEAD 拦截）；其他击杀的保护流程保持不变。
     */
    public static boolean piercesProtection(@Nullable Identifier deathReason, boolean force) {
        return force && SparkWitchDeathReasons.TIME_STOLEN.equals(deathReason);
    }

    /**
     * Who a settling curse is attributed to. An online stealer who is still the exact Time Stealer is the killer
     * unless SparkFactionAPI's structural veto refuses him, in which case the curse is spent (toll parity). A
     * stealer who is offline or changed role leaves the kill unattributed (owner decision Q7: the curse still kills).
     * 结算中的诅咒归属于谁。在线且仍为精确窃时者时记为击杀者，除非 SparkFactionAPI 结构性否决拒绝他，
     * 此时诅咒作废（与丧钟一致）。窃时者离线或已换职业时击杀无归属（所有者决定 Q7：诅咒照样致死）。
     */
    public static SettleActor settleActor(boolean stealerOnline, boolean stillTimeStealer, boolean affectAllowed) {
        if (!stealerOnline || !stillTimeStealer) {
            return SettleActor.UNATTRIBUTED;
        }
        return affectAllowed ? SettleActor.ATTRIBUTED : SettleActor.VETOED;
    }

    /**
     * Owner decision Q5: only the real NoellesRoles Timekeeper lifts curses. The purchase must be the time-reduction
     * item, the buyer's current role must be exactly {@code noellesroles:time_keeper}, alive in an ACTIVE round, and
     * their effective faction known and not {@code wathe:killer} (an Impostor Timekeeper is killer faction; a Coroner
     * disguise keeps the Coroner role). The check is literally "not KILLER", not "is CIVILIAN".
     * 所有者决定 Q5：只有真正的 NoellesRoles 计时员能解除诅咒。购买的必须是减少时间的物品，买家当前职业必须精确为
     * {@code noellesroles:time_keeper}，在 ACTIVE 对局中存活，且有效阵营已知并且不是 {@code wathe:killer}
     * （内鬼计时员属于杀手阵营；验尸官伪装仍保留验尸官职业）。判定字面为“不是杀手”，而非“是平民”。
     */
    public static boolean countsAsTimekeeperRescue(
            boolean reduceTimeItem,
            boolean exactTimekeeper,
            @Nullable Identifier effectiveFaction,
            boolean buyerAlive,
            boolean gameActive
    ) {
        return reduceTimeItem && exactTimekeeper && buyerAlive && gameActive
                && effectiveFaction != null && !FactionIds.KILLER.equals(effectiveFaction);
    }

    /** Settle attribution outcome. / 结算归属结果。 */
    public enum SettleActor {
        /** The stealer is recorded as the killer. / 窃时者被记为击杀者。 */
        ATTRIBUTED,
        /** The kill proceeds with no killer. / 无击杀者照常击杀。 */
        UNATTRIBUTED,
        /** Structural veto: the curse is spent without a kill. / 结构性否决：诅咒作废、不击杀。 */
        VETOED
    }
}
