package dev.caecorthus.sparkwitch.client.wraith;

/** Maps the synchronized Wraith phase to its exact grayscale strength. / 将同步的冤魂阶段映射为精确灰阶强度。 */
public final class WraithVisionRules {
    public static final float RESTRICTED_DESATURATION = 1.0f;
    public static final float PROMOTED_DESATURATION = 0.5f;

    private WraithVisionRules() {
    }

    public static float desaturation(boolean active, boolean restricted) {
        if (!active) {
            return 0.0f;
        }
        return restricted ? RESTRICTED_DESATURATION : PROMOTED_DESATURATION;
    }
}
