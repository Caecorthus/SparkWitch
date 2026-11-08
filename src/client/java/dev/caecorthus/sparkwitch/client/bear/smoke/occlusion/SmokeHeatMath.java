package dev.caecorthus.sparkwitch.client.bear.smoke.occlusion;

/**
 * Pure rules of the heat-image smoke mask ({@link SmokeHeatMask}, BEAR WP5 for SparkStrength's Undercover thermal, S4):
 * argument checks of the cross-mod entry, the heat-target rectangle the passes cover, and the CPU reference of what
 * {@code heat_mask.fsh} writes per pixel. A client-side driver can render into its own heat framebuffer, call the
 * facade, read the pixels back and compare them with {@link #maskedHeat} / {@link #hazedHeat} fed with
 * {@code BearSmokeViewApi.opticalDepth} along the same pixel ray; tests pin the shader to these rules.
 * 热成像图像烟雾遮罩（{@link SmokeHeatMask}，BEAR WP5，供 SparkStrength 卧底热成像 S4 使用）的纯规则：跨模组入口的参数
 * 检查、各步骤覆盖的热图矩形，以及 {@code heat_mask.fsh} 逐像素写入结果的 CPU 参照。客户端驱动可以渲染到自己的热图帧缓冲、
 * 调用门面、回读像素，并与用同一像素视线上的 {@code BearSmokeViewApi.opticalDepth} 算出的 {@link #maskedHeat} /
 * {@link #hazedHeat} 比较；测试把着色器固定到这些规则上。
 */
public final class SmokeHeatMath {
    /**
     * Owner Q6 (2026-11-06, "能，但是就是一坨亮白"): hot smoke shows in the thermal view as a bright white blob, as hot
     * as a body; the heat that fully opaque smoke writes, on the heat target's 0..1 scale where a body is 1.
     * 所有者 Q6（2026-11-06，“能，但是就是一坨亮白”）：热烟在热成像中显示为一团亮白，与人体一样热；完全不透明的烟写入的热值，
     * 按热图 0..1 的刻度（人体为 1）。
     */
    public static final float DEFAULT_HAZE = 1.0F;
    /** Largest heat target edge accepted, in pixels. / 接受的最大热图边长（像素）。 */
    public static final int MAX_TARGET_SIZE = 16384;
    /**
     * Pass pixels around the smoke rectangle a mask texel can still affect (2 march texels, as in the outline mask).
     * 遮罩纹素仍可影响的烟雾矩形外围像素数（2 个步进纹素，与描边遮罩相同）。
     */
    static final int RECT_MARGIN = 2 * SmokeOcclusionMath.MARCH_DIVISOR;

    private SmokeHeatMath() {
    }

    /** Texture names and size the facade accepts. / 门面接受的纹理名与尺寸。 */
    public static boolean validTarget(int colorTexture, int depthTexture, int width, int height) {
        return colorTexture > 0 && depthTexture > 0 && colorTexture != depthTexture
                && width > 0 && height > 0 && width <= MAX_TARGET_SIZE && height <= MAX_TARGET_SIZE;
    }

    /** A haze heat in {@code (0, 1]}. / 位于 {@code (0, 1]} 的热雾热值。 */
    public static boolean validHaze(float heat) {
        return heat > 0.0F && heat <= 1.0F;
    }

    /**
     * Heat-target rectangle {@code [x0, y0, x1, y1)} (GL origin, bottom-left) covering the pass-pixel smoke rectangle
     * grown by {@link #RECT_MARGIN}, scaled from the pass size to the heat size and clamped; null when it is empty.
     * 覆盖渲染目标像素烟雾矩形（外扩 {@link #RECT_MARGIN}）的热图矩形 {@code [x0, y0, x1, y1)}（GL 原点在左下），从渲染尺寸
     * 缩放到热图尺寸并钳制；为空时返回 null。
     */
    public static int[] heatRect(int[] passRect, int passWidth, int passHeight, int width, int height) {
        if (passRect == null || passWidth <= 0 || passHeight <= 0) {
            return null;
        }
        int[] grown = SmokeOcclusionMath.grow(passRect, RECT_MARGIN, passWidth, passHeight);
        int x0 = (int) Math.floor((double) grown[0] * width / passWidth);
        int y0 = (int) Math.floor((double) grown[1] * height / passHeight);
        int x1 = (int) Math.ceil((double) grown[2] * width / passWidth);
        int y1 = (int) Math.ceil((double) grown[3] * height / passHeight);
        int[] rect = SmokeOcclusionMath.grow(new int[]{x0, y0, x1, y1}, 0, width, height);
        return rect[2] > rect[0] && rect[3] > rect[1] ? rect : null;
    }

    /**
     * Mask pass: the heat a pixel keeps behind {@code opticalDepth} of smoke between the eye and its depth (the same
     * visibility as an outline: 1 up to optical depth 0.3, 0 from 1.5, linear between).
     * 遮罩步骤：像素在眼睛与其深度之间有 {@code opticalDepth} 的烟时保留的热值（与描边相同的可见度：光学厚度 0.3 以内为 1，
     * 1.5 起为 0，其间线性）。
     */
    public static float maskedHeat(float heat, float opticalDepth) {
        return heat * SmokeOcclusionMath.visibility(opticalDepth);
    }

    /**
     * Haze pass: the heat after the smoke in front of the pixel's depth glows with {@code haze} (blended with MAX).
     * 热雾步骤：像素深度之前的烟以 {@code haze} 发热之后的热值（按 MAX 混合）。
     */
    public static float hazedHeat(float heat, float haze, float opticalDepth) {
        return Math.max(heat, haze * (1.0F - SmokeOcclusionMath.visibility(opticalDepth)));
    }
}
