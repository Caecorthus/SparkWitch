package dev.caecorthus.sparkwitch.roles.neutral.sphinxhao;

import org.jetbrains.annotations.Nullable;

/**
 * Pure decisions of Sphinx Hao's two server skills (CORE): the outcome of a press, when the private notice is due,
 * when a crawler is released, when a 豪气冲天 window ends and the cooldown it leaves. No world access; the services
 * feed it snapshots. Numbers live in {@link SphinxHaoRules}.
 * 狮身豪两个服务端技能的纯判断（CORE）：一次按键的结果、私人提示何时到期、陆泳者何时被释放、豪气冲天窗口何时结束以及之后的
 * 冷却。不访问世界；由服务传入快照。数值位于 {@link SphinxHaoRules}。
 */
public final class SphinxHaoSkillRules {
    public static final String INFECT_SUCCESS_KEY = "message.sparkwitch.sphinx_hao.infect.success";
    public static final String INFECT_NO_TARGET_KEY = "message.sparkwitch.sphinx_hao.infect.no_target";
    public static final String INFECT_ALREADY_KEY = "message.sparkwitch.sphinx_hao.infect.already";
    public static final String INFECT_COOLDOWN_KEY = "message.sparkwitch.sphinx_hao.infect.cooldown";
    /** The victim's private actionbar line 「你感觉自己有点豪」. / 被感染者的私人动作栏文字「你感觉自己有点豪」。 */
    public static final String NOTICE_KEY = "message.sparkwitch.sphinx_hao.notice";
    public static final String ROAR_START_KEY = "message.sparkwitch.sphinx_hao.roar.start";
    public static final String ROAR_COOLDOWN_KEY = "message.sparkwitch.sphinx_hao.roar.cooldown";
    public static final String ROAR_BUSY_KEY = "message.sparkwitch.sphinx_hao.roar.busy";

    private SphinxHaoSkillRules() {
    }

    /** What a 嘉豪病毒 press does after the caster gate. / 通过施放者门槛后，一次嘉豪病毒按键的结果。 */
    public enum InfectOutcome {
        /** Still cooling: say how long, change nothing. / 仍在冷却：提示剩余时间，不改变任何东西。 */
        COOLING,
        /** No eligible player in the crosshair within range. / 准星范围内没有合格玩家。 */
        NO_TARGET,
        /** The crosshair player is already infected: refused, no cooldown. / 准星玩家已被感染：拒绝，不进冷却。 */
        ALREADY_INFECTED,
        /** SparkFactionAPI vetoed the target: silent, no cooldown. / SparkFactionAPI 否决目标：静默，不进冷却。 */
        VETOED,
        /** Go on to the Fear re-check, then infect. / 继续检查恐惧，然后感染。 */
        INFECT
    }

    /**
     * Guard order after {@code mayCast}: cooldown, target, already infected, SparkFactionAPI veto. The Fear re-check
     * runs after this (it tells the caster why on its own).
     * {@code mayCast} 之后的守卫顺序：冷却、目标、是否已感染、SparkFactionAPI 否决。之后再检查恐惧（恐惧会自行提示原因）。
     */
    public static InfectOutcome infectOutcome(int cooldownRemaining, boolean targetFound, boolean alreadyInfected,
                                              boolean policyAllows) {
        if (cooldownRemaining > 0) {
            return InfectOutcome.COOLING;
        }
        if (!targetFound) {
            return InfectOutcome.NO_TARGET;
        }
        if (alreadyInfected) {
            return InfectOutcome.ALREADY_INFECTED;
        }
        return policyAllows ? InfectOutcome.INFECT : InfectOutcome.VETOED;
    }

    /** What a 豪气冲天 press does after the caster gate. / 通过施放者门槛后，一次豪气冲天按键的结果。 */
    public enum RoarOutcome {
        COOLING,
        /** Another window already runs in this world (a forced second Sphinx Hao). / 本世界已有窗口进行中（强制的第二名狮身豪）。 */
        BUSY,
        CAST
    }

    public static RoarOutcome roarOutcome(int cooldownRemaining, boolean windowRunning) {
        if (cooldownRemaining > 0) {
            return RoarOutcome.COOLING;
        }
        return windowRunning ? RoarOutcome.BUSY : RoarOutcome.CAST;
    }

    /** Whether an unshown notice is due at {@code now}. / 未显示的提示在 {@code now} 时是否到期。 */
    public static boolean noticeDue(long now, long noticeTick, boolean noticed) {
        return !noticed && now >= noticeTick;
    }

    /**
     * A crawler leaves the window early when it is no longer a living, visible participant: offline, dead, a living
     * spectator (Rift Gate occupant) or swallowed by the Taotie.
     * 陆泳者不再是存活、可见的参与者时提前离开窗口：离线、死亡、活体旁观（裂隙门内）或被饕餮吞下。
     */
    public static boolean releasesCrawler(boolean online, boolean playingAndAlive, boolean spectator,
                                          boolean swallowed) {
        return !online || !playingAndAlive || spectator || swallowed;
    }

    /** Why a running window ends at {@code now}; null keeps it running. / 进行中的窗口在 {@code now} 结束的原因；null 表示继续。 */
    public enum RoarEnd {
        /** The full 10 s ran out. / 完整的 10 秒结束。 */
        ELAPSED,
        /** Early: the round is no longer ACTIVE. / 提前：回合不再处于 ACTIVE。 */
        ROUND_STOPPED,
        /** Early: the caster is offline, dead or no longer a Sphinx Hao (reset). / 提前：施法者离线、死亡或不再是狮身豪（重置）。 */
        CASTER_GONE
    }

    public static @Nullable RoarEnd roarEnd(long now, long endTick, boolean roundActive, boolean casterOnline,
                                            boolean casterAlive, boolean casterStillSphinxHao) {
        if (!roundActive) {
            return RoarEnd.ROUND_STOPPED;
        }
        if (!casterOnline || !casterAlive || !casterStillSphinxHao) {
            return RoarEnd.CASTER_GONE;
        }
        return now >= endTick ? RoarEnd.ELAPSED : null;
    }

    /**
     * 豪气冲天's ready tick once its window ends at {@code actualEnd}: 30 s counted from the real end (house rule).
     * 豪气冲天窗口在 {@code actualEnd} 结束后的就绪刻：从实际结束起算 30 秒（项目惯例）。
     */
    public static long roarReadyTickAfter(long actualEnd) {
        return actualEnd + SphinxHaoRules.ROAR_COOLDOWN_TICKS;
    }

    /** Whole seconds shown for a tick count (rounded up, at least 1 while positive). / 显示用整秒数（向上取整）。 */
    public static int secondsCeil(int ticks) {
        return ticks <= 0 ? 0 : (ticks + 19) / 20;
    }
}
