package dev.caecorthus.sparkwitch.client.hud;

/**
 * Pure layout and state rules of {@link HiroHudRenderer}: which 死亡回归 line shows and where the two corner rows sit.
 * {@link HiroHudRenderer} 的纯布局与状态规则：显示哪一条死亡回归提示，以及右下角两行的位置。
 */
final class HiroHudRules {
    static final int RIGHT_PADDING = 5;
    static final int BOTTOM_PADDING = 5;
    static final int LINE_GAP = 2;

    private HiroHudRules() {
    }

    enum ReturnLine {
        /** The window is running: remaining seconds. / 窗口进行中：剩余秒数。 */
        WINDOW,
        /** Cooling down: remaining seconds. / 冷却中：剩余秒数。 */
        COOLDOWN,
        /** Castable but for the mana. / 仅差魔力。 */
        NO_MANA,
        /** Castable: key hint. / 可施放：按键提示。 */
        READY
    }

    /**
     * A running window wins over the cooldown (the cooldown starts only when the skill ends), the cooldown over the
     * mana shortfall, and only a castable skill shows the key hint.
     * 进行中的窗口优先于冷却（冷却在技能结束后才开始），冷却优先于魔力不足，只有可施放时才显示按键提示。
     */
    static ReturnLine returnLine(boolean windowActive, boolean onCooldown, int mana, int manaCost) {
        if (windowActive) {
            return ReturnLine.WINDOW;
        }
        if (onCooldown) {
            return ReturnLine.COOLDOWN;
        }
        return mana < manaCost ? ReturnLine.NO_MANA : ReturnLine.READY;
    }

    /**
     * Top y of a corner row; row 0 is the bottom one. Matches the two rows {@code SaintHudLayoutRules} reserves for
     * the role ({@code fontHeight * 2 + 2 + 5}), so the Karma line stacks above.
     * 右下角某一行的顶部 y；第 0 行在最下方。与 {@code SaintHudLayoutRules} 为本职业预留的两行（{@code fontHeight * 2 + 2 + 5}）
     * 一致，业障行叠放在其上方。
     */
    static int rowY(int screenHeight, int fontHeight, int row) {
        return screenHeight - BOTTOM_PADDING - fontHeight - row * (fontHeight + LINE_GAP);
    }

    static int rightAlignedX(int screenWidth, int textWidth) {
        return Math.max(0, screenWidth - RIGHT_PADDING - textWidth);
    }
}
