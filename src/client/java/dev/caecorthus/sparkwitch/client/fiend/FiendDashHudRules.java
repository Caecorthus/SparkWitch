package dev.caecorthus.sparkwitch.client.fiend;

/**
 * Pure presentation rules of the moment Fiend's owner-only Dash line: "ready: press G" or "cooling down Ns", from the
 * remaining Dash cooldown synced only to that Fiend. Text only and only the local player's own state, so it never leaks
 * another player. One right-aligned line in the bottom-right corner with the shared skill line's 5 px padding (the
 * Fiend has no witch skill, so that row is normally free; if it is ever occupied the line moves up one row). When the
 * line would reach the centred hotbar column (narrow GUI widths) it is lifted above the hotbar, Wathe's stamina row and
 * its cooldown number (Blind / {@code SeekerHudRules} precedent). Role-owned presentation; never the witch skill
 * inventory panel and never the action bar.
 * 时刻中魔人仅本人可见的疾驰行的纯展示规则：依据只同步给该魔人的疾驰剩余冷却显示“就绪：按 G”或“冷却 N 秒”。
 * 只有文字，且只读本地玩家自己的状态，因此绝不会泄露其他玩家。在右下角右对齐显示一行，边距与共享技能行相同
 * （5 像素；魔人没有魔女技能，该行通常空闲；若被占用则上移一行）。该行会伸入居中快捷栏列时（GUI 较窄），抬升到
 * 快捷栏、Wathe 体力行及其冷却数字之上（参照盲人与 {@code SeekerHudRules}）。属于职业自有展示，从不使用魔女技能
 * 背包面板，也从不使用动作栏。
 */
public final class FiendDashHudRules {
    public static final String READY_KEY = "hud.sparkwitch.fiend.dash.ready";
    public static final String COOLDOWN_KEY = "hud.sparkwitch.fiend.dash.cooldown";
    /** Fiend tint, shared with the moment countdown. / 魔人色调，与时刻倒计时相同。 */
    public static final int READY_COLOR = FiendMomentHudRules.TEXT_COLOR;
    /** Same muted grey as the Blind's cooldown lines. / 与盲人冷却行相同的灰色。 */
    public static final int COOLDOWN_COLOR = 0xFF8C99A6;
    public static final int RIGHT_PADDING = 5;
    public static final int BOTTOM_PADDING = 5;
    public static final int ROW_GAP = 2;
    /** Half the vanilla hotbar width (182 / 2). / 原版快捷栏宽度的一半（182 / 2）。 */
    public static final int HOTBAR_HALF_WIDTH = 91;
    /** Horizontal gap kept from the hotbar. / 与快捷栏保持的水平间距。 */
    public static final int HOTBAR_GAP = 4;
    /**
     * Bottom offset of the lifted line: above Wathe's stamina row (top at height - 39) and its cooldown number, + 2 px.
     * 抬升后该行的底部偏移：位于 Wathe 体力行（顶端为 height - 39）及其冷却数字之上，再留 2 像素。
     */
    public static final int LIFTED_BOTTOM_OFFSET = 41;
    /**
     * Seconds value used only to measure the line, so the lift decision does not flip as the countdown loses a digit.
     * 仅用于测量行宽的秒数，使抬升判断不会因倒计时少一位数字而来回跳动。
     */
    public static final int MEASURE_SECONDS = 99;

    private FiendDashHudRules() {
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
     * Drawn only on the moment Fiend's own client while it is playing and alive, in an ACTIVE round once Wathe's
     * round-start fade is over, with Wathe's train HUD shown and F1 off, and never while a Taotie has swallowed it
     * (the server ends the moment on a swallow; this hides the line until that resync arrives).
     * 仅在时刻中的魔人自己的客户端上、其参与且存活、回合为 ACTIVE 且 Wathe 开局淡入结束、Wathe 列车 HUD 显示且未按 F1 时
     * 绘制；被饕餮吞下时从不绘制（服务端会因吞噬结束时刻；此处在该同步到达前隐藏该行）。
     */
    public static boolean showsHud(boolean hudHidden, boolean gameActive, int fade, boolean trainHudActive,
                                   boolean momentFiend, boolean playingAndAlive, boolean swallowed) {
        return !hudHidden && gameActive && fade <= 0 && trainHudActive && momentFiend && playingAndAlive && !swallowed;
    }

    /** Whole seconds, rounded up, never negative. / 向上取整的整秒数，不为负。 */
    public static int seconds(int ticks) {
        return ticks <= 0 ? 0 : (ticks + 19) / 20;
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
        return Math.max(0, scaledWidth - RIGHT_PADDING - Math.max(0, textWidth));
    }

    /**
     * Whether a line starting at {@code lineX} would reach into the centred hotbar column (hotbar, stamina row,
     * cooldown number).
     * 从 {@code lineX} 开始的行是否会伸入居中的快捷栏列（快捷栏、体力行、冷却数字）。
     */
    public static boolean overlapsHotbar(int scaledWidth, int lineX) {
        return lineX < scaledWidth / 2 + HOTBAR_HALF_WIDTH + HOTBAR_GAP;
    }

    /**
     * Top y of the line: lifted above the hotbar column when the measured line would reach it, else the 5 px padding
     * plus one row when the shared bottom-right skill line is occupied.
     * 该行的顶部 y：测量宽度会伸入快捷栏列时抬升到其上方，否则为 5 像素边距，共享右下角技能行被占用时再上移一行。
     */
    public static int rowY(int scaledWidth, int scaledHeight, int measuredWidth, int fontHeight,
                           boolean sharedLineOccupied) {
        int bottomOffset;
        if (overlapsHotbar(scaledWidth, rowX(scaledWidth, measuredWidth))) {
            bottomOffset = LIFTED_BOTTOM_OFFSET;
        } else {
            bottomOffset = BOTTOM_PADDING + (sharedLineOccupied ? fontHeight + ROW_GAP : 0);
        }
        return scaledHeight - bottomOffset - fontHeight;
    }

    /** The seconds the line is measured with (see {@link #MEASURE_SECONDS}). / 测量行宽所用的秒数。 */
    public static int measuredSeconds(int seconds) {
        return seconds < 0 ? seconds : Math.max(seconds, MEASURE_SECONDS);
    }
}
