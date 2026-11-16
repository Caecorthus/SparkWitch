package dev.caecorthus.sparkwitch.client.factor;

/** Last-resort colors never replace an existing instinct or defeat hiding. / 后备颜色不覆盖已有本能，也不突破隐藏。 */
public final class WitchFactorOutlineRules {
    public static final int FACTOR_COLOR = 0x8B70DB;
    public static final int EMMA_COLOR = 0xF29BC3;

    private WitchFactorOutlineRules() {
    }

    public static int resolve(int originalColor, boolean hardHidden, boolean explicitSkip,
                              boolean visibleEmma, boolean visibleCarrier) {
        return resolve(originalColor, hardHidden, explicitSkip, visibleEmma, visibleCarrier, false, -1);
    }

    /**
     * {@code roleColored}: the target is a self-made witch the viewer may see; {@code roleColor} then replaces the
     * factor violet, whether or not the target still carries a factor. Same last-resort rules as the other two.
     * {@code roleColored}：目标是观看者获准看到的自成魔女；此时以 {@code roleColor} 取代因子紫色，无论目标是否仍携带因子。
     * 与另外两种后备色遵循相同的最后兜底规则。
     */
    public static int resolve(int originalColor, boolean hardHidden, boolean explicitSkip,
                              boolean visibleEmma, boolean visibleCarrier, boolean roleColored, int roleColor) {
        // -1 alone means absent; ARGB provider colors may also be negative. / 仅 -1 表示无颜色；提供方 ARGB 色也可能为负数。
        if (originalColor != -1 || hardHidden || explicitSkip) {
            return originalColor;
        }
        if (visibleEmma) {
            return EMMA_COLOR;
        }
        if (roleColored) {
            return roleColor;
        }
        return visibleCarrier ? FACTOR_COLOR : -1;
    }
}
