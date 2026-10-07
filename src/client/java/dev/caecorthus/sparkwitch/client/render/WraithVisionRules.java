package dev.caecorthus.sparkwitch.client.render;

/**
 * Maps synchronized Wraith phases to one non-stacking desaturation strength.
 * 将同步的冤魂阶段映射为单一、不叠加的去饱和强度。
 */
public final class WraithVisionRules {
    public static final float RESTRICTED_DESATURATION = 1.0F;
    public static final float PROMOTED_DESATURATION = 0.5F;

    private WraithVisionRules() {
    }

    /** Compose once, after selecting existing Witch vision, without changing its brightness or spread.
     * 在现有魔女视野选定后一次合成，不改变亮度或扩散效果。 */
    public static float composeLastEscape(float existingFactor, float lastEscapeFactor) {
        return Math.max(existingFactor, lastEscapeFactor);
    }

    /** Escape selects the strongest active effect; otherwise preserve Wraith's legacy priority.
     * 脱险时取所有生效效果的最大灰阶；平时保留冤魂原有优先级。同强度保留原效果。 */
    public static boolean usePerception(float existingFactor, boolean perceptionActive, boolean escapeActive) {
        return perceptionActive && (escapeActive ? existingFactor < 1.0f : existingFactor <= 0.0f);
    }

    /** On a tie retain Witch's existing effect, including its luma coefficients.
     * 同强度保留 Witch 原效果及其亮度系数。 */
    public static Vision composeLastEscape(Vision existing, float[] escape) {
        if (escape == null || escape[0] <= existing.desaturation()) {
            return existing;
        }
        return new Vision(escape[0], escape[1], escape[2], 1.0f, 0.299f, 0.587f, 0.114f);
    }

    public record Vision(float desaturation, float spread, float brightness,
                         float luminance, float red, float green, float blue) {
    }

    public static float desaturation(boolean active, boolean restricted) {
        if (!active) {
            return 0.0F;
        }
        return restricted ? RESTRICTED_DESATURATION : PROMOTED_DESATURATION;
    }
}
