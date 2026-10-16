package dev.caecorthus.sparkwitch.client.fiend;

/**
 * Pure presentation rules of the dormant Fiend's owner-only feign-death line: "feign death in Ns" while it waits for
 * the next feign, "reviving in Ns" while it lies as its fake corpse, from the {@code sparkwitch:fiend_feign} state the
 * server syncs only to that Fiend. Text only and only the local player's own state, so it never reveals the Fiend or
 * which corpse is fake. It takes the Dash line's bottom-right row and layout ({@link FiendDashHudRules#rowX},
 * {@link FiendDashHudRules#rowY}): only the moment Fiend has a Dash line and the moment Fiend never has this one, so the
 * two never meet. Role-owned presentation; never the witch skill inventory panel and never the action bar.
 * 休眠魔人仅本人可见的假死行的纯展示规则：等待下一次假死时显示“假死倒计时 N 秒”，作为假尸体躺下时显示“复活倒计时
 * N 秒”，数据来自服务端只同步给该魔人的 {@code sparkwitch:fiend_feign}。只有文字，且只读本地玩家自己的状态，因此绝不会
 * 暴露魔人身份或哪具尸体是假的。占用疾驰行所在的右下角行并沿用其布局（{@link FiendDashHudRules#rowX}、
 * {@link FiendDashHudRules#rowY}）：只有时刻中的魔人有疾驰行，而时刻中的魔人从不显示本行，二者因此不会重叠。属于职业
 * 自有展示，从不使用魔女技能背包面板，也从不使用动作栏。
 */
public final class FiendFeignHudRules {
    public static final String NEXT_KEY = "hud.sparkwitch.fiend.feign.next";
    public static final String REVIVE_KEY = "hud.sparkwitch.fiend.feign.revive";
    /** Fiend tint, shared with the Dash ready line and the moment countdown. / 魔人色调，与疾驰就绪行及时刻倒计时相同。 */
    public static final int COLOR = FiendDashHudRules.READY_COLOR;

    private FiendFeignHudRules() {
    }

    /** The line's translation key, its whole-seconds argument and colour. / 该行的翻译键、整秒参数与颜色。 */
    public record Line(String key, int seconds, int color) {
    }

    /**
     * Drawn only on a Fiend's own client while the server shows it a feign cycle, the Fiend is not the moment Fiend,
     * Wathe counts it playing and alive, in an ACTIVE round once Wathe's round-start fade is over, with Wathe's train HUD
     * shown and F1 off, and never while a Taotie holds it (Dash parity). There is deliberately no spectator gate: a
     * feigning Fiend is in vanilla spectator mode yet never marked dead in Wathe, and that is when "reviving" must show.
     * The synced shown flag, not {@code FiendParticipation.isDormantFiend} (its spent check is server-only), is what
     * hides the line for a spent Fiend.
     * 仅在魔人自己的客户端上、服务端向其显示假死周期、不是时刻中的魔人、Wathe 认定其参与且存活、回合为 ACTIVE 且 Wathe
     * 开局淡入结束、Wathe 列车 HUD 显示且未按 F1 时绘制；被饕餮吞下时从不绘制（与疾驰一致）。刻意不设旁观门槛：假死中的
     * 魔人处于原版旁观模式，但在 Wathe 中从未被标记死亡，“复活倒计时”正需要在此时显示。耗尽的魔人由同步的显示标记隐藏，
     * 而非 {@code FiendParticipation.isDormantFiend}（其耗尽检查仅在服务端有效）。
     */
    public static boolean showsHud(boolean hudHidden, boolean gameActive, int fade, boolean trainHudActive,
                                   boolean fiend, boolean momentFiend, boolean playingAndAlive, boolean swallowed,
                                   boolean shown) {
        return !hudHidden && gameActive && fade <= 0 && trainHudActive && fiend && !momentFiend && playingAndAlive
                && !swallowed && shown;
    }

    /** Whole seconds, rounded up, never negative (the Dash rounding). / 向上取整的整秒数，不为负（与疾驰相同）。 */
    public static int seconds(int ticks) {
        return FiendDashHudRules.seconds(ticks);
    }

    /**
     * The revival countdown while feigning, else the countdown to the next feign.
     * 假死中为复活倒计时，否则为距下一次假死的倒计时。
     */
    public static Line line(boolean feigning, int remainingTicks) {
        return new Line(feigning ? REVIVE_KEY : NEXT_KEY, seconds(remainingTicks), COLOR);
    }
}
