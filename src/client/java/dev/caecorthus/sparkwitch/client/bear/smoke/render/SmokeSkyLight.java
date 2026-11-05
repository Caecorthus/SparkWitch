package dev.caecorthus.sparkwitch.client.bear.smoke.render;

/**
 * Pure direct light for the smoke's single scattering: the sun while it is up, otherwise the moon, from Minecraft's
 * sky angle (the sky is rotated -90° about Y, then by the angle about X, so the sun rises in the east, +X), dimmed by
 * rain and warmed near the horizon. The colour already carries strength and smoke albedo.
 * 烟雾单次散射所用的纯直射光：太阳在天上时取太阳，否则取月亮；方向来自 Minecraft 天空角（天空先绕 Y 轴转 -90°，再绕 X 轴
 * 转该角度，因此太阳从东方 +X 升起），下雨时变暗，近地平线时偏暖。颜色已包含强度与烟雾反照率。
 */
public final class SmokeSkyLight {
    private SmokeSkyLight() {
    }

    /**
     * Writes the direction toward the light (out[0..2], unit) and its colour (out[3..5]); the colour is 0 when neither
     * body is above the horizon.
     * 写出指向光源的方向（out[0..2]，单位向量）及其颜色（out[3..5]）；日月都不在地平线上时颜色为 0。
     *
     * @param skyAngle     {@code World#getSkyAngle}, 0 at noon / 正午为 0
     * @param rainGradient {@code World#getRainGradient}, 0..1
     * @param moonSize     {@code World#getMoonSize}, 0..1 (moon phase) / 月相
     */
    public static void compute(float skyAngle, float rainGradient, float moonSize, float[] out) {
        double angle = skyAngle * 2.0 * Math.PI;
        float sunX = (float) -Math.sin(angle);
        float sunY = (float) Math.cos(angle);
        float weather = 1.0F - 0.85F * clamp01(rainGradient);
        float sunUp = smoothstep(-0.05F, 0.2F, sunY);
        if (sunUp > 0.0F) {
            out[0] = sunX;
            out[1] = sunY;
            out[2] = 0.0F;
            float warm = smoothstep(0.0F, 0.45F, sunY);
            float strength = SmokeMarchRules.SUN_STRENGTH * sunUp * weather;
            out[3] = 1.0F * strength * SmokeMarchRules.ALBEDO_R;
            out[4] = lerp(0.58F, 0.96F, warm) * strength * SmokeMarchRules.ALBEDO_G;
            out[5] = lerp(0.34F, 0.90F, warm) * strength * SmokeMarchRules.ALBEDO_B;
            return;
        }
        out[0] = -sunX;
        out[1] = -sunY;
        out[2] = 0.0F;
        float strength = SmokeMarchRules.MOON_STRENGTH * smoothstep(-0.05F, 0.2F, -sunY) * clamp01(moonSize) * weather;
        out[3] = 0.62F * strength * SmokeMarchRules.ALBEDO_R;
        out[4] = 0.72F * strength * SmokeMarchRules.ALBEDO_G;
        out[5] = 0.95F * strength * SmokeMarchRules.ALBEDO_B;
    }

    /**
     * Sky colour divided by its luminance (a tint near 1), for the bluish shadows of smoke under the sky.
     * 天空颜色除以其亮度（接近 1 的色调），用于见天烟雾略带蓝色的阴影。
     */
    public static void tint(double r, double g, double b, float[] out) {
        double luminance = Math.max(0.05, 0.2126 * r + 0.7152 * g + 0.0722 * b);
        out[0] = (float) Math.min(2.0, r / luminance);
        out[1] = (float) Math.min(2.0, g / luminance);
        out[2] = (float) Math.min(2.0, b / luminance);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0F - 2.0F * t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
