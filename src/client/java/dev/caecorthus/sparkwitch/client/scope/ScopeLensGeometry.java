package dev.caecorthus.sparkwitch.client.scope;

/**
 * Pure lens geometry shared by the lens shader uniforms and the HUD, so the shader's lens edge, the HUD rim ring and
 * the reticle's {@link ScopeFrame#lensRadius()} always agree. Units are whatever pixels the caller passes (framebuffer
 * pixels for the shader, scaled GUI pixels for the HUD).
 * 镜片着色器 uniform 与 HUD 共用的纯镜片几何，使着色器的镜片边缘、HUD 镜框环与分划的 {@link ScopeFrame#lensRadius()}
 * 始终一致。单位取决于调用方（着色器用帧缓冲像素，HUD 用缩放后的 GUI 像素）。
 */
public final class ScopeLensGeometry {
    /** Lens radius as a share of the screen's short side (owner brief: about 0.42). / 镜片半径占屏幕短边的比例。 */
    public static final float LENS_RADIUS_SHARE = 0.42F;
    /**
     * Barrel distortion k of both lens composites ({@code DISTORTION} in {@code sparkwitch_scope_lens.fsh} and
     * {@code sparkwitch_scope_pip.fsh}, pinned equal by a test): the lens pixel r lens radii from the centre shows the
     * picture at r (1 + k r^4). The shaders keep their own literal (post shaders cannot import); this is the Java copy
     * for reticles that place marks on the distorted picture. Changing it alone changes no rendering.
     * 两个镜片合成着色器的桶形畸变系数 k（{@code sparkwitch_scope_lens.fsh} 与 {@code sparkwitch_scope_pip.fsh} 中的
     * {@code DISTORTION}，由测试保证相等）：距中心 r 个镜片半径的镜片像素显示画面中 r (1 + k r^4) 处的内容。着色器保留
     * 各自的字面量（后处理着色器无法 import）；这是供分划在畸变画面上定位刻度的 Java 副本，单独修改它不会改变任何渲染。
     */
    public static final double BARREL_DISTORTION = 0.06;
    /** Outer edge of the HUD fallback's dark tube rim, in lens radii. / HUD 回退暗色镜筒边的外缘（镜片半径倍数）。 */
    public static final float FALLBACK_RIM_OUTER = 1.07F;
    /** HUD fallback colours: dark and semi-transparent, never fully black. / HUD 回退颜色：深色半透明，绝不全黑。 */
    public static final int FALLBACK_RIM_INNER_COLOR = 0xD80A0A0C;
    public static final int FALLBACK_RIM_OUTER_COLOR = 0xB00A0A0C;
    public static final int FALLBACK_PERIPHERY_COLOR = 0x8C0A0A0C;
    /**
     * With less than full periphery blur, the fallback's dark periphery first fades out over this many lens radii past
     * the tube rim, roughly like the shader's rim falloff (which ends about 0.21 radii out). / 镜外模糊不满时，回退的暗色
     * 镜外区域先在镜筒边外这么多个镜片半径内淡出，与着色器的镜筒边衰减（约 0.21 个半径处结束）大致相同。
     */
    public static final float FALLBACK_RIM_FADE = 0.2F;
    /** Crisp lens rim ring: from one physical pixel inside the edge, fading out over a few pixels outside. / 清晰镜框环。 */
    public static final float RIM_RING_INSIDE_PIXELS = 1.0F;
    public static final float RIM_RING_OUTSIDE_PIXELS = 2.5F;
    public static final int RIM_RING_INNER_COLOR = 0xE6141416;
    public static final int RIM_RING_OUTER_COLOR = 0x00141416;
    public static final int MIN_RING_SEGMENTS = 48;
    public static final int MAX_RING_SEGMENTS = 256;
    /** Longest chord of a ring segment, in physical pixels. / 环段最长弦长（物理像素）。 */
    public static final double RING_SEGMENT_PIXELS = 6.0;

    private ScopeLensGeometry() {
    }

    /**
     * The HUD fallback periphery's colour at {@code t} (0 at the tube rim's outer edge, 1 at the screen corner) for a
     * periphery blur of {@code blur}: the full gradient from {@link #FALLBACK_RIM_OUTER_COLOR} to
     * {@link #FALLBACK_PERIPHERY_COLOR} with its alpha scaled by {@code blur}, so 1 is today's darkening and 0 leaves the
     * view clear past the rim. / HUD 回退镜外区域在 {@code t}（镜筒边外缘为 0，屏幕角为 1）处、镜外模糊为 {@code blur} 时的
     * 颜色：从 {@link #FALLBACK_RIM_OUTER_COLOR} 到 {@link #FALLBACK_PERIPHERY_COLOR} 的完整渐变，透明度乘以 {@code blur}；
     * 1 即原有压暗，0 时镜筒边之外清晰。
     */
    public static int fallbackPeripheryColor(float blur, float t) {
        float b = Float.isFinite(blur) ? Math.max(0.0F, Math.min(1.0F, blur)) : 1.0F;
        float k = Float.isFinite(t) ? Math.max(0.0F, Math.min(1.0F, t)) : 1.0F;
        int from = FALLBACK_RIM_OUTER_COLOR >>> 24;
        int to = FALLBACK_PERIPHERY_COLOR >>> 24;
        int alpha = Math.round(b * (from + (to - from) * k));
        return (alpha << 24) | (FALLBACK_PERIPHERY_COLOR & 0x00FFFFFF);
    }

    /** Lens radius for a screen of this size. / 该尺寸屏幕的镜片半径。 */
    public static double lensRadius(double width, double height) {
        return Math.max(0.0, Math.min(width, height)) * LENS_RADIUS_SHARE;
    }

    /** Distance from the centre to the farthest screen corner: a ring this wide covers the screen. / 中心到最远角的距离。 */
    public static double coverRadius(double width, double height, double centerX, double centerY) {
        double dx = Math.max(centerX, width - centerX);
        double dy = Math.max(centerY, height - centerY);
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Segment count of a ring with this outer radius in physical pixels: chords of at most
     * {@link #RING_SEGMENT_PIXELS}, within [{@link #MIN_RING_SEGMENTS}, {@link #MAX_RING_SEGMENTS}].
     * 外半径（物理像素）对应的环段数：弦长不超过 {@link #RING_SEGMENT_PIXELS}，并限制在给定范围内。
     */
    public static int ringSegments(double outerRadiusPixels) {
        if (!(outerRadiusPixels > 0.0)) {
            return MIN_RING_SEGMENTS;
        }
        double segments = Math.ceil(2.0 * Math.PI * outerRadiusPixels / RING_SEGMENT_PIXELS);
        return (int) Math.max(MIN_RING_SEGMENTS, Math.min(MAX_RING_SEGMENTS, segments));
    }
}
