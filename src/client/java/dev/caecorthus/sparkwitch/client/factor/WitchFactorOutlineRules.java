package dev.caecorthus.sparkwitch.client.factor;

/** Last-resort colors never replace an existing instinct or defeat hiding. / 后备颜色不覆盖已有本能，也不突破隐藏。 */
public final class WitchFactorOutlineRules {
    public static final int FACTOR_COLOR = 0x8B70DB;
    public static final int EMMA_COLOR = 0xF29BC3;

    private WitchFactorOutlineRules() {
    }

    public static int resolve(int originalColor, boolean hardHidden, boolean explicitSkip,
                              int privateRevealColor, boolean visibleEmma, boolean visibleCarrier) {
        // -1 alone means absent; ARGB provider colors may also be negative. / 仅 -1 表示无颜色；提供方 ARGB 色也可能为负数。
        if (originalColor != -1 || hardHidden || explicitSkip) {
            return originalColor;
        }
        if (privateRevealColor != -1) {
            return privateRevealColor;
        }
        if (visibleEmma) {
            return EMMA_COLOR;
        }
        return visibleCarrier ? FACTOR_COLOR : -1;
    }
}
