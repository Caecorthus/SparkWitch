package dev.caecorthus.sparkwitch.client.bear.smoke.occlusion;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Pure rules of the smoke outline mask: march resolution and the screen rectangles the passes cover (used here), and
 * the constants the shaders share. The Java reference of the per-pixel GLSL rules in {@code occlusion_march.fsh} and
 * {@code outline_mask.fsh} (checkpoint crossing and texel encoding, fail-closed sealing, optical depth and visibility at
 * a distance, depth linearisation, which silhouette pixel an outline pixel belongs to) lives in the test sources
 * ({@code SmokeShaderReference}); tests pin the shaders to those rules and these shared constants.
 * 烟雾描边遮罩的纯规则：步进分辨率与各步骤覆盖的屏幕矩形（此处使用），以及着色器共享的常量。{@code occlusion_march.fsh}、
 * {@code outline_mask.fsh} 逐像素规则的 Java 参照（检查点穿越与纹素编码、失败即遮挡封口、某视距处的光学厚度与可见度、
 * 深度线性化、描边像素归属哪个剪影像素）位于测试源码（{@code SmokeShaderReference}）；测试把着色器固定到这些规则与共享常量上。
 */
public final class SmokeOcclusionMath {
    /** The occlusion texture is this many times coarser than the pass on each axis. / 遮挡纹理每轴比渲染目标粗这么多倍。 */
    public static final int MARCH_DIVISOR = 2;
    /** Ray-march step in blocks: half a smoke cell. / 步进长度（格）：半个烟雾小格。 */
    public static final float STEP_LENGTH = 0.25F;
    /** Volumes one march can sample (one texture unit each). / 单次步进可采样的浓度体数（各占一个纹理单元）。 */
    public static final int MAX_VOLUMES = 8;
    /**
     * Further clouds in view beyond {@link #MAX_VOLUMES} are passed as plain boxes that fail closed; past this many they
     * merge into the last box. / 超出 {@link #MAX_VOLUMES} 的视野内烟团以失败即遮挡的盒子传入；再超出则并入最后一个盒子。
     */
    public static final int MAX_SEALED_BOXES = 24;
    /**
     * Chebyshev radius from an outline pixel to the silhouette pixel it came from. Vanilla's entity_outline chain is
     * a 1-texel sobel plus two 2-texel box blurs, so no outline pixel lies farther than 3 pixels from its body.
     * 描边像素到其来源剪影像素的切比雪夫半径。原版 entity_outline 链为 1 纹素 sobel 加两次 2 纹素盒式模糊，
     * 因此任何描边像素离其身体都不超过 3 像素。
     */
    public static final int SILHOUETTE_SEARCH_RADIUS = 3;

    private SmokeOcclusionMath() {
    }

    /** Occlusion texture size for one pass axis. / 某一轴的遮挡纹理尺寸。 */
    public static int marchSize(int passSize) {
        return Math.max(1, (passSize + MARCH_DIVISOR - 1) / MARCH_DIVISOR);
    }

    /**
     * Pixel rectangle {@code [x0, y0, x1, y1)} (GL window origin, bottom-left) of a pass that the given camera-relative
     * boxes can touch, grown by {@code margin} and clamped to the pass, or null when every box is outside the view
     * frustum. A box crossing the eye plane covers the whole pass.
     * 给定相机相对盒子可能触及的渲染目标像素矩形 {@code [x0, y0, x1, y1)}（GL 窗口原点在左下），外扩 {@code margin} 并
     * 钳制在目标内；所有盒子都在视锥外时返回 null。跨过眼平面的盒子覆盖整个目标。
     *
     * @param boxes {@code minX, minY, minZ, maxX, maxY, maxZ} per box, camera-relative
     */
    public static int[] screenRect(Matrix4f viewProjection, double[] boxes, int count, int width, int height,
                                   int margin) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        boolean any = false;
        boolean whole = false;
        Vector4f corner = new Vector4f();
        for (int b = 0; b < count && !whole; b++) {
            int base = b * 6;
            int outside = 0x3F;
            boolean behind = false;
            float bMinX = Float.POSITIVE_INFINITY;
            float bMinY = Float.POSITIVE_INFINITY;
            float bMaxX = Float.NEGATIVE_INFINITY;
            float bMaxY = Float.NEGATIVE_INFINITY;
            for (int c = 0; c < 8; c++) {
                corner.set((float) boxes[base + ((c & 1) != 0 ? 3 : 0)],
                        (float) boxes[base + 1 + ((c & 2) != 0 ? 3 : 0)],
                        (float) boxes[base + 2 + ((c & 4) != 0 ? 3 : 0)], 1.0F);
                viewProjection.transform(corner);
                int flags = 0;
                if (corner.x < -corner.w) flags |= 1;
                if (corner.x > corner.w) flags |= 2;
                if (corner.y < -corner.w) flags |= 4;
                if (corner.y > corner.w) flags |= 8;
                if (corner.z < -corner.w) flags |= 16;
                if (corner.z > corner.w) flags |= 32;
                outside &= flags;
                if (corner.w <= 1.0E-4F) {
                    behind = true;
                } else {
                    bMinX = Math.min(bMinX, corner.x / corner.w);
                    bMinY = Math.min(bMinY, corner.y / corner.w);
                    bMaxX = Math.max(bMaxX, corner.x / corner.w);
                    bMaxY = Math.max(bMaxY, corner.y / corner.w);
                }
            }
            if (outside != 0) {
                continue;
            }
            any = true;
            if (behind) {
                whole = true;
                continue;
            }
            minX = Math.min(minX, bMinX);
            minY = Math.min(minY, bMinY);
            maxX = Math.max(maxX, bMaxX);
            maxY = Math.max(maxY, bMaxY);
        }
        if (!any) {
            return null;
        }
        if (whole) {
            return new int[]{0, 0, width, height};
        }
        int x0 = (int) Math.floor((Math.max(-1.0F, minX) * 0.5F + 0.5F) * width) - margin;
        int y0 = (int) Math.floor((Math.max(-1.0F, minY) * 0.5F + 0.5F) * height) - margin;
        int x1 = (int) Math.ceil((Math.min(1.0F, maxX) * 0.5F + 0.5F) * width) + margin;
        int y1 = (int) Math.ceil((Math.min(1.0F, maxY) * 0.5F + 0.5F) * height) + margin;
        return clamp(new int[]{x0, y0, x1, y1}, width, height);
    }

    /** Grows a rectangle and clamps it to {@code [0, width) x [0, height)}. / 外扩矩形并钳制到目标范围内。 */
    public static int[] grow(int[] rect, int margin, int width, int height) {
        return clamp(new int[]{rect[0] - margin, rect[1] - margin, rect[2] + margin, rect[3] + margin}, width, height);
    }

    /** The occlusion texels covering a pass-pixel rectangle. / 覆盖某渲染目标像素矩形的遮挡纹素。 */
    public static int[] toMarchRect(int[] pixelRect, int marchWidth, int marchHeight) {
        return clamp(new int[]{
                Math.floorDiv(pixelRect[0], MARCH_DIVISOR) - 1,
                Math.floorDiv(pixelRect[1], MARCH_DIVISOR) - 1,
                Math.floorDiv(pixelRect[2] + MARCH_DIVISOR - 1, MARCH_DIVISOR) + 1,
                Math.floorDiv(pixelRect[3] + MARCH_DIVISOR - 1, MARCH_DIVISOR) + 1
        }, marchWidth, marchHeight);
    }

    private static int[] clamp(int[] rect, int width, int height) {
        rect[0] = Math.max(0, Math.min(width, rect[0]));
        rect[1] = Math.max(0, Math.min(height, rect[1]));
        rect[2] = Math.max(rect[0], Math.min(width, rect[2]));
        rect[3] = Math.max(rect[1], Math.min(height, rect[3]));
        return rect;
    }
}
