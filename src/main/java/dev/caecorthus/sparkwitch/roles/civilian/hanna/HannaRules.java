package dev.caecorthus.sparkwitch.roles.civilian.hanna;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 远野汉娜 Tono Hanna — a passenger who drifts (PR #212, reworked in review; owner decisions 2026-11-11).
 * <ul>
 *   <li>Float (ability key): {@link #FLOAT_MANA} mana for {@link #FLOAT_TICKS}: Speed II, her own actions are silent
 *   for everyone else, and nobody's instinct can outline her. Ready {@link #INITIAL_COOLDOWN_TICKS} after the round
 *   starts; the {@link #COOLDOWN_TICKS} cooldown starts when the float ENDS (naturally or interrupted).</li>
 *   <li>Interruption: a kill attempt, a left-click attack, a right-click interaction or a projectile hit from another
 *   living player ends the float at once and starts {@link #PENALTY_TICKS} of penalty, one
 *   {@link #PENALTY_SANITY_PER_SECOND} sanity loss per second. Nothing else interrupts (owner: no "in range" rule).</li>
 *   <li>Mana: {@link #INITIAL_MANA} at deal time, {@link #TASK_MANA} per task, no natural regeneration and no kill
 *   reward (the numbers live in {@code WitchManaRules}).</li>
 *   <li>Coins: {@link #TASK_COINS} per task and {@link #PASSIVE_COINS} every {@link #PASSIVE_INTERVAL_TICKS}.</li>
 *   <li>Shop: the parasol and the handkerchief; whoever holds either item can use it (owner 2026-11-11).</li>
 * </ul>
 * 远野汉娜——会漂浮的乘客（PR #212，审核中重写；所有者 2026-11-11 决定）。
 * 浮游（技能键）：消耗 {@link #FLOAT_MANA} 魔力，持续 {@link #FLOAT_TICKS}：速度 II、自身行动对其他人静音、任何人的本能都
 * 描不出她。开局 {@link #INITIAL_COOLDOWN_TICKS} 后就绪；{@link #COOLDOWN_TICKS} 冷却从浮游结束（自然结束或被打断）起算。
 * 打断：其他存活玩家的击杀尝试、左键攻击、右键交互或投掷物命中会立刻结束浮游，并开始 {@link #PENALTY_TICKS} 的惩罚，每秒流失
 * {@link #PENALTY_SANITY_PER_SECOND} 理智。除此之外不打断（所有者：不做「生效范围」）。
 * 魔力：开局 {@link #INITIAL_MANA}，每个任务 +{@link #TASK_MANA}，无自然回复、无击杀奖励（数值在 {@code WitchManaRules}）。
 * 金币：每个任务 {@link #TASK_COINS}，每 {@link #PASSIVE_INTERVAL_TICKS} 再得 {@link #PASSIVE_COINS}。
 * 商店：洋伞与手帕；谁拿到都能正常使用（所有者 2026-11-11）。
 */
public final class HannaRules {

    public static final Identifier ROLE_ID = SparkWitch.id("hanna");

    /** Identity colour FFAF9F. / 身份色 FFAF9F。 */
    public static final int COLOR = 0xFFAF9F;

    /** Stable item contracts. / 稳定物品契约。 */
    public static final Identifier UMBRELLA_ID = SparkWitch.id("hanna_umbrella");
    public static final Identifier HANDKERCHIEF_ID = SparkWitch.id("hanna_handkerchief");

    /** Forced-cooldown store id for the float. / 浮游的强制冷却存储 id。 */
    public static final Identifier FLOAT_SKILL_ID = SparkWitch.id("hanna_float");

    /** Transient movement-speed modifier worn during the float. / 浮游期间的临时移动速度修饰符。 */
    public static final Identifier FLOAT_SPEED_MODIFIER_ID = SparkWitch.id("hanna_float_speed");
    /** Speed II: +20% per level, multiplied on the total. / 速度 II：每级 +20%，乘在总值上。 */
    public static final double FLOAT_SPEED_BONUS = 0.4D;

    /** Float: 40 mana for 10 s. / 浮游：40 魔力，10 秒。 */
    public static final int FLOAT_TICKS = 10 * 20;
    public static final int FLOAT_MANA = 40;
    /** Ready 40 s into the round; 45 s after each float ends. / 开局 40 秒后就绪；每次浮游结束后 45 秒。 */
    public static final int INITIAL_COOLDOWN_TICKS = 40 * 20;
    public static final int COOLDOWN_TICKS = 45 * 20;

    /** Mana: 40 at deal time, +20 per task. / 魔力：开局 40，每个任务 +20。 */
    public static final int INITIAL_MANA = 40;
    public static final int TASK_MANA = 20;

    /** Interrupted float: 10 s, 5% sanity per second. / 被打断：10 秒，每秒 5% 理智。 */
    public static final int PENALTY_TICKS = 10 * 20;
    public static final float PENALTY_SANITY_PER_SECOND = 0.05F;

    /** Income: 50 per task, 15 every 30 s. / 收入：每个任务 50，每 30 秒 15。 */
    public static final int TASK_COINS = 50;
    public static final int PASSIVE_COINS = 15;
    public static final int PASSIVE_INTERVAL_TICKS = 30 * 20;

    /**
     * Parasol: 150 coins, Slow Falling II while held in either hand, 25 durability spent at 1 per second held;
     * right-click opens it for 10 s of Slow Falling II that needs no holding.
     * 洋伞：150 金币，握在任一只手时缓降 II，25 点耐久、手持每秒 -1；右键撑开获得 10 秒无需手持的缓降 II。
     */
    public static final int UMBRELLA_PRICE = 150;
    public static final int UMBRELLA_DURABILITY = 25;
    public static final int UMBRELLA_OPEN_TICKS = 10 * 20;
    public static final int SLOW_FALLING_AMPLIFIER = 1;

    /**
     * Handkerchief: 100 coins, hold right-click for 1 s; then 5 s of +6% sanity per second and the float penalty ends.
     * 2 uses, 15 s between uses.
     * 手帕：100 金币，按住右键 1 秒生效；之后 5 秒内每秒 +6% 理智，并解除浮游惩罚。可用 2 次，每次间隔 15 秒。
     */
    public static final int HANDKERCHIEF_PRICE = 100;
    public static final int HANDKERCHIEF_HOLD_TICKS = 20;
    public static final int HANDKERCHIEF_TICKS = 5 * 20;
    public static final float HANDKERCHIEF_SANITY_PER_SECOND = 0.06F;
    public static final int HANDKERCHIEF_USES = 2;
    public static final int HANDKERCHIEF_COOLDOWN_TICKS = 15 * 20;

    private HannaRules() {
    }

    public static boolean isHanna(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    /** Passive coins due at world time {@code worldTime}. / 世界时间对应的被动金币。 */
    public static int passiveIncomeAt(long worldTime) {
        return worldTime > 0L && worldTime % PASSIVE_INTERVAL_TICKS == 0 ? PASSIVE_COINS : 0;
    }

    /**
     * Whether a once-per-second pulse of a timed window is due: true 1 s, 2 s, ... after the window started, the last
     * one on the tick it ends. {@code endTick <= 0} means no window.
     * 定时窗口的每秒脉冲是否到点：窗口开始后第 1、2……秒各一次，最后一次落在窗口结束的那一刻。{@code endTick <= 0} 表示无窗口。
     */
    public static boolean pulseDue(long now, long endTick, int windowTicks) {
        if (endTick <= 0L) {
            return false;
        }
        long remaining = endTick - now;
        return remaining >= 0L && remaining < windowTicks && remaining % 20L == 0L;
    }

    /** What the server does with a float request. / 服务端如何处理一次浮游请求。 */
    public enum FloatVerdict {
        /** Start the float. / 开始浮游。 */
        USE,
        /** Refuse and tell her she lacks mana. / 拒绝并提示魔力不足。 */
        NO_MANA,
        /** Refuse with the Fear feedback. / 拒绝并给出恐惧反馈。 */
        FEARED,
        /** Refuse silently. / 静默拒绝。 */
        REFUSE
    }

    /**
     * Pure gate for {@code sparkwitch:use_hanna_float}. Role, round and control gates refuse silently; then readiness
     * (also refused while a float is running), then Fear, then mana, so a blocked or cooling request never reports a
     * mana problem.
     * {@code sparkwitch:use_hanna_float} 的纯判定。职业、回合与受控门槛静默拒绝；其后依次是就绪（浮游进行中同样拒绝）、
     * 恐惧、魔力，因此被拦截或冷却中的请求不会提示魔力问题。
     */
    public static FloatVerdict floatVerdict(boolean hanna, boolean roundActive, boolean aliveParticipant,
                                            boolean swallowed, boolean stunned, boolean roleSkillBlocked,
                                            boolean floating, boolean ready, boolean feared, boolean hasMana) {
        if (!hanna || !roundActive || !aliveParticipant || swallowed || stunned || roleSkillBlocked
                || floating || !ready) {
            return FloatVerdict.REFUSE;
        }
        if (feared) {
            return FloatVerdict.FEARED;
        }
        return hasMana ? FloatVerdict.USE : FloatVerdict.NO_MANA;
    }

    /**
     * Whether another player's touch (attack, right-click, projectile, kill attempt) interrupts a running float: the
     * toucher is unknown (no owner) or is another player who is alive in the round and not a spectator. Hanna never
     * interrupts herself, and dead spectators clicking through her never count.
     * 他人的接触（攻击、右键、投掷物、击杀尝试）是否打断进行中的浮游：接触者未知（无归属），或是另一名在局内存活且非旁观的玩家。
     * 汉娜自己不会打断自己，已死亡的旁观者点到她也不算。
     */
    public static boolean interrupts(boolean floating, boolean toucherKnown, boolean toucherIsSelf,
                                     boolean toucherAliveParticipant) {
        if (!floating) {
            return false;
        }
        return !toucherKnown || (!toucherIsSelf && toucherAliveParticipant);
    }
}
