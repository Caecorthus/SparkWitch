package dev.caecorthus.sparkwitch.client.athlete;

import dev.caecorthus.sparkwitch.client.fiend.FiendDashHudRules;
import dev.caecorthus.sparkwitch.roles.civilian.athlete.AthleteRules;
import dev.caecorthus.sparkwitch.util.TickTime;

/**
 * Pure presentation rules of the Athlete's owner-only Dash line: "ready: press G" or "cooling down Ns", from the
 * remaining Dash cooldown synced only to that Athlete. Text only and only the local player's own state, so it never
 * leaks another player. It takes the Fiend Dash line's bottom-right layout as is (5 px padding, one row up over an
 * occupied shared skill line, lifted above the hotbar column at narrow GUI widths); an Athlete is never a Fiend, so the
 * two lines never meet. Role-owned presentation; never the witch skill inventory panel and never the action bar.
 * 运动员仅本人可见的冲刺行的纯展示规则：依据只同步给该运动员的冲刺剩余冷却显示“就绪：按 G”或“冷却 N 秒”。只有文字，
 * 且只读本地玩家自己的状态，因此绝不会泄露其他玩家。直接沿用魔人疾驰行的右下角布局（5 像素边距、共享技能行被占用时
 * 上移一行、GUI 较窄时抬升到快捷栏列之上）；运动员不可能是魔人，两行不会同时出现。属于职业自有展示，从不使用魔女技能
 * 背包面板，也从不使用动作栏。
 */
public final class AthleteDashHudRules {
    public static final String READY_KEY = "hud.sparkwitch.athlete.dash.ready";
    public static final String COOLDOWN_KEY = "hud.sparkwitch.athlete.dash.cooldown";
    /** Opaque Athlete role colour. / 不透明的运动员职业色。 */
    public static final int READY_COLOR = 0xFF000000 | AthleteRules.COLOR;
    /** Same muted grey as the Blind's and the Fiend's cooldown lines. / 与盲人、魔人冷却行相同的灰色。 */
    public static final int COOLDOWN_COLOR = 0xFF8C99A6;
    /** Shared bottom-right layout (Fiend Dash / Blind kit precedent). / 共享的右下角布局（参照魔人疾驰与盲人道具）。 */
    public static final int RIGHT_PADDING = FiendDashHudRules.RIGHT_PADDING;
    public static final int BOTTOM_PADDING = FiendDashHudRules.BOTTOM_PADDING;
    public static final int ROW_GAP = FiendDashHudRules.ROW_GAP;
    public static final int LIFTED_BOTTOM_OFFSET = FiendDashHudRules.LIFTED_BOTTOM_OFFSET;
    public static final int MEASURE_SECONDS = FiendDashHudRules.MEASURE_SECONDS;

    private AthleteDashHudRules() {
    }

    /**
     * The line's translation key, whether it takes the ability-key hint, its seconds argument (negative = none) and
     * colour.
     * 该行的翻译键、是否带技能按键提示、秒数参数（负数表示无）与颜色。
     */
    public record Line(String key, boolean keyHint, int seconds, int color) {
        public boolean hasSeconds() {
            return seconds >= 0;
        }
    }

    /**
     * Drawn only on the Athlete's own client while it is playing and alive, in an ACTIVE round once Wathe's round-start
     * fade is over, with Wathe's train HUD shown and F1 off, and never while a Taotie has swallowed it (the server
     * refuses a swallowed dash, so no "ready: press G" there).
     * 仅在运动员自己的客户端上、其参与且存活、回合为 ACTIVE 且 Wathe 开局淡入结束、Wathe 列车 HUD 显示且未按 F1 时绘制；
     * 被饕餮吞下时从不绘制（服务端会拒绝被吞下时的冲刺，因此不显示“就绪：按 G”）。
     */
    public static boolean showsHud(boolean hudHidden, boolean gameActive, int fade, boolean trainHudActive,
                                   boolean athlete, boolean playingAndAlive, boolean swallowed) {
        return !hudHidden && gameActive && fade <= 0 && trainHudActive && athlete && playingAndAlive && !swallowed;
    }

    /** Whole seconds, rounded up, never negative. / 向上取整的整秒数，不为负。 */
    public static int seconds(int ticks) {
        return TickTime.ceilSeconds(ticks);
    }

    /**
     * Ready (with the key hint) when no cooldown remains, else the cooldown in whole seconds rounded up.
     * 无剩余冷却时为就绪（带按键提示），否则为向上取整的冷却秒数。
     */
    public static Line line(int dashCooldownTicks) {
        if (dashCooldownTicks > 0) {
            return new Line(COOLDOWN_KEY, false, seconds(dashCooldownTicks), COOLDOWN_COLOR);
        }
        return new Line(READY_KEY, true, -1, READY_COLOR);
    }

    /** Left x of a right-aligned line. / 右对齐行的左侧 x。 */
    public static int rowX(int scaledWidth, int textWidth) {
        return FiendDashHudRules.rowX(scaledWidth, textWidth);
    }

    /**
     * Top y of the line, with the shared corner layout (see {@link FiendDashHudRules#rowY}).
     * 该行的顶部 y，使用共享的角落布局（见 {@link FiendDashHudRules#rowY}）。
     */
    public static int rowY(int scaledWidth, int scaledHeight, int measuredWidth, int fontHeight,
                           boolean sharedLineOccupied) {
        return FiendDashHudRules.rowY(scaledWidth, scaledHeight, measuredWidth, fontHeight, sharedLineOccupied);
    }

    /** The seconds the line is measured with (see {@link #MEASURE_SECONDS}). / 测量行宽所用的秒数。 */
    public static int measuredSeconds(int seconds) {
        return FiendDashHudRules.measuredSeconds(seconds);
    }
}
