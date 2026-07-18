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

    public static float desaturation(boolean active, boolean restricted) {
        if (!active) {
            return 0.0F;
        }
        return restricted ? RESTRICTED_DESATURATION : PROMOTED_DESATURATION;
    }
}
