package dev.caecorthus.sparkwitch.client.bear.smoke.occlusion;

import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeOcclusion;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Pure rules of the smoke outline mask: march resolution and the screen rectangles the passes cover (used here), plus
 * the Java reference of the per-pixel GLSL rules in {@code occlusion_march.fsh} and {@code outline_mask.fsh} (checkpoint
 * crossing and texel encoding, fail-closed sealing, optical depth and visibility at a distance, depth linearisation,
 * which silhouette pixel an outline pixel belongs to); tests pin the shaders to these rules and shared constants.
 * 烟雾描边遮罩的纯规则：步进分辨率与各步骤覆盖的屏幕矩形（此处使用），以及 {@code occlusion_march.fsh}、
 * {@code outline_mask.fsh} 逐像素规则的 Java 参照（检查点穿越与纹素编码、失败即遮挡封口、某视距处的光学厚度与可见度、
 * 深度线性化、描边像素归属哪个剪影像素）；测试把着色器固定到这些规则与共享常量上。
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
    /** Window depth of an empty silhouette texel (the cleared value). / 空剪影纹素的窗口深度（清除值）。 */
    public static final float NO_SILHOUETTE = 1.0F;

    private SmokeOcclusionMath() {
    }

    /** Occlusion texture size for one pass axis. / 某一轴的遮挡纹理尺寸。 */
    public static int marchSize(int passSize) {
        return Math.max(1, (passSize + MARCH_DIVISOR - 1) / MARCH_DIVISOR);
    }

    /**
     * Eye distance at which a threshold is crossed inside one march segment {@code [t, t + step]} whose optical depth
     * grows from {@code before} by {@code added} (linear inside the segment).
     * 某段步进 {@code [t, t + step]} 内光学厚度从 {@code before} 增加 {@code added}（段内线性）时，阈值被穿越处的视距。
     */
    public static float crossing(float t, float step, float before, float added, float threshold) {
        return t + step * (threshold - before) / added;
    }

    /**
     * Packs one ray's march into a texel ({@link SmokeOcclusion} layout): {@code crossings[i]} is the eye distance where
     * checkpoint {@code i} was reached or {@link SmokeOcclusion#NEVER}; the first unreached checkpoint after a reached one
     * carries {@code -totalDepth}.
     * 把一条视线的步进结果打包成纹素（{@link SmokeOcclusion} 布局）：{@code crossings[i]} 为达到检查点 {@code i} 的视距或
     * {@link SmokeOcclusion#NEVER}；已达到检查点之后的第一个未达到检查点存 {@code -totalDepth}。
     */
    public static float[] encode(float[] crossings, float totalDepth) {
        float[] texel = new float[SmokeOcclusion.CHECKPOINT_COUNT];
        for (int i = 0; i < texel.length; i++) {
            boolean reached = crossings[i] < SmokeOcclusion.NEVER;
            boolean previousReached = i > 0 && crossings[i - 1] < SmokeOcclusion.NEVER;
            texel[i] = reached ? crossings[i] : previousReached ? -totalDepth : SmokeOcclusion.NEVER;
        }
        return texel;
    }

    /**
     * Fails a texel closed at {@code entry}: smoke the march could not sample (more clouds in view than texture units)
     * counts as fully hidden from its box onward, so every checkpoint is reached there at the latest.
     * 让纹素在 {@code entry} 处失败即遮挡：步进无法采样的烟（视野内烟团多于纹理单元）从其盒子起视为完全遮住，因此每个
     * 检查点最迟在该处达到。
     */
    public static float[] seal(float[] texel, float entry) {
        float[] sealed = texel.clone();
        for (int i = 0; i < sealed.length; i++) {
            float distance = sealed[i] < 0.0F ? SmokeOcclusion.NEVER : sealed[i];
            sealed[i] = Math.min(distance, entry);
        }
        return sealed;
    }

    /**
     * Optical depth at eye distance {@code distance} decoded from a texel: 0 before the first checkpoint, piecewise
     * linear between reached checkpoints, the ray total past the last one (an upper bound, fail closed), and the next
     * checkpoint's depth when data is missing.
     * 从纹素解出视距 {@code distance} 处的光学厚度：第一个检查点之前为 0；已达到的检查点之间分段线性；超过最后一个已达到的
     * 检查点后取视线总量（上界，失败即遮挡）；数据缺失时取下一个检查点的厚度。
     */
    public static float opticalDepthAt(float[] texel, float distance) {
        float first = texel[0];
        if (first < 0.0F || first >= SmokeOcclusion.NEVER || distance < first) {
            return 0.0F;
        }
        float previousDistance = first;
        float previousDepth = SmokeOcclusion.checkpointOpticalDepth(0);
        for (int i = 1; i < SmokeOcclusion.CHECKPOINT_COUNT; i++) {
            float value = texel[i];
            float depth = SmokeOcclusion.checkpointOpticalDepth(i);
            if (value < 0.0F) {
                return -value;
            }
            if (value >= SmokeOcclusion.NEVER) {
                return depth;
            }
            if (distance < value) {
                float span = Math.max(value - previousDistance, 1.0E-4F);
                return previousDepth + (depth - previousDepth) * (distance - previousDistance) / span;
            }
            previousDistance = value;
            previousDepth = depth;
        }
        return SmokeOcclusion.HIDDEN_OPTICAL_DEPTH;
    }

    /**
     * Alpha an outline keeps behind {@code opticalDepth}: 1 up to the fade start, 0 from the hidden depth, linear between.
     * 描边在光学厚度 {@code opticalDepth} 之后保留的 alpha：开始变淡前为 1，达到消失厚度为 0，其间线性。
     */
    public static float visibility(float opticalDepth) {
        float fade = SmokeOcclusion.FADE_START_OPTICAL_DEPTH;
        float span = SmokeOcclusion.HIDDEN_OPTICAL_DEPTH - fade;
        return 1.0F - Math.max(0.0F, Math.min(1.0F, (opticalDepth - fade) / span));
    }

    /**
     * Linear eye distance of a window-space depth at normalised device coordinates, through the inverse of
     * {@code projection * view} (camera-relative). An empty texel ({@link #NO_SILHOUETTE}) is infinitely far.
     * 归一化设备坐标处窗口深度对应的线性视距，经由 {@code projection * view} 的逆矩阵（以相机为原点）。空纹素
     * （{@link #NO_SILHOUETTE}）视为无限远。
     */
    public static float eyeDistance(Matrix4f inverseViewProjection, float ndcX, float ndcY, float windowDepth) {
        if (windowDepth >= NO_SILHOUETTE) {
            return Float.POSITIVE_INFINITY;
        }
        Vector4f point = inverseViewProjection.transform(new Vector4f(ndcX, ndcY, windowDepth * 2.0F - 1.0F, 1.0F));
        float x = point.x / point.w;
        float y = point.y / point.w;
        float z = point.z / point.w;
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    /**
     * The silhouette depth an outline pixel is judged by, from the window depths around it ({@code depths[dy][dx]}
     * for offsets {@code -R..R}, R = {@link #SILHOUETTE_SEARCH_RADIUS}): the pixel's own depth when it is a silhouette
     * pixel, otherwise the farthest depth of the nearest Chebyshev ring holding any, otherwise {@link #NO_SILHOUETTE}.
     * Taking the farthest in a tie fails closed: an edge between a visible and a smoked body follows the smoked one.
     * 描边像素据以判断的剪影深度（{@code depths[dy][dx]}，偏移 {@code -R..R}，R = {@link #SILHOUETTE_SEARCH_RADIUS}）：
     * 本身是剪影像素时取自身深度；否则取最近一圈有剪影的切比雪夫环中最远的深度；都没有时为 {@link #NO_SILHOUETTE}。
     * 同环取最远即失败即遮挡：可见身体与烟中身体之间的边缘跟随烟中那一个。
     */
    public static float silhouetteDepth(float[][] depths) {
        int radius = SILHOUETTE_SEARCH_RADIUS;
        float centre = depths[radius][radius];
        if (centre < NO_SILHOUETTE) {
            return centre;
        }
        for (int r = 1; r <= radius; r++) {
            float farthest = -1.0F;
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) {
                        continue;
                    }
                    float depth = depths[radius + dy][radius + dx];
                    if (depth < NO_SILHOUETTE) {
                        farthest = Math.max(farthest, depth);
                    }
                }
            }
            if (farthest >= 0.0F) {
                return farthest;
            }
        }
        return NO_SILHOUETTE;
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
