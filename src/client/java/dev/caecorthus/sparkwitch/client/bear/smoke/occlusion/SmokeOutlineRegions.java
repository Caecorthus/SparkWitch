package dev.caecorthus.sparkwitch.client.bear.smoke.occlusion;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen rectangles that hold every outline-layer pixel of one world pass, so the smoke occlusion march and the outline
 * mask only touch the part of the smoke where an outline can actually be (most of a smoke-filled view holds none).
 * Each outline draw is bounded on the CPU from its own vertices under the draw-time {@code ProjMat * ModelViewMat}
 * (what {@code rendertype_outline.vsh} applies), grown by {@link #MARGIN} for rasterisation and the entity_outline
 * chain's spread, and merged into at most {@link #MAX_REGIONS} rectangles. A draw it cannot bound (no float position,
 * a vertex at or behind the eye) makes the pass unbounded, and so does a pass whose outlines were never seen: callers
 * then fall back to the whole smoke rectangle (fail closed). Render thread only; pure apart from reading vertex memory.
 * 一次世界渲染中所有描边层像素所在的屏幕矩形，使烟雾遮挡步进与描边遮罩只处理烟中真正可能有描边的部分（充满烟的画面
 * 大多没有描边）。每次描边绘制都在 CPU 上按其自身顶点与绘制时的 {@code ProjMat * ModelViewMat}（即
 * {@code rendertype_outline.vsh} 所用变换）求出边界，外扩 {@link #MARGIN}（光栅化与 entity_outline 后处理的扩散），并合并为
 * 至多 {@link #MAX_REGIONS} 个矩形。无法求出边界的绘制（没有浮点位置、有顶点位于眼睛处或其后方）会使本次渲染变为无界，
 * 从未见到描边绘制的渲染也一样：调用方此时退回整个烟雾矩形（失败即遮挡）。仅限渲染线程；除读取顶点内存外为纯逻辑。
 */
public final class SmokeOutlineRegions {
    /** Rectangles kept per pass; more draws are merged into them. / 每次渲染保留的矩形数；更多的绘制并入其中。 */
    public static final int MAX_REGIONS = 4;
    /**
     * Pixels added around a draw's vertex bounds: one for rasterisation, the entity_outline chain's reach
     * ({@link SmokeOcclusionMath#SILHOUETTE_SEARCH_RADIUS}) and one spare.
     * 绘制顶点边界外扩的像素：光栅化 1 像素、entity_outline 后处理的扩散（{@link SmokeOcclusionMath#SILHOUETTE_SEARCH_RADIUS}）
     * 及 1 像素余量。
     */
    public static final int MARGIN = SmokeOcclusionMath.SILHOUETTE_SEARCH_RADIUS + 2;
    private static final float MIN_W = 1.0E-4F;

    private final List<int[]> regions = new ArrayList<>(MAX_REGIONS + 1);
    private boolean seen;
    private boolean unbounded;

    /** Starts a new pass. / 开始新的一次渲染。 */
    public void reset() {
        regions.clear();
        seen = false;
        unbounded = false;
    }

    /** No usable bounds: no outline draw was seen, or one could not be bounded. / 无可用边界：未见描边绘制，或某次绘制无法求界。 */
    public boolean unbounded() {
        return unbounded || !seen;
    }

    /** The merged pixel rectangles {@code [x0, y0, x1, y1)}; valid when not {@link #unbounded()}. / 合并后的像素矩形。 */
    public List<int[]> regions() {
        return regions;
    }

    /** Marks the pass unbounded (fail closed). / 把本次渲染标为无界（失败即遮挡）。 */
    public void markUnbounded() {
        seen = true;
        unbounded = true;
    }

    /**
     * Bounds one draw of {@code vertexCount} vertices at native address {@code address} ({@code stride} bytes apart,
     * three floats of position at {@code positionOffset}) under {@code clipFromVertex}, in a {@code width x height}
     * pass. / 在 {@code width x height} 的渲染中，按 {@code clipFromVertex} 求一次绘制的边界：{@code vertexCount} 个顶点
     * 位于本机地址 {@code address}，间隔 {@code stride} 字节，位置为 {@code positionOffset} 处的三个浮点数。
     */
    public void addDraw(Matrix4f clipFromVertex, long address, int stride, int positionOffset, int vertexCount,
                        int width, int height) {
        seen = true;
        if (unbounded || vertexCount <= 0) {
            return;
        }
        // Model-space box first (cheap), then its eight corners; only a box that reaches the eye plane needs every
        // vertex. / 先求模型空间包围盒（廉价），再投影其 8 个角；只有触及眼平面的盒子才需要逐顶点投影。
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        long p = address + positionOffset;
        for (int i = 0; i < vertexCount; i++, p += stride) {
            float x = MemoryUtil.memGetFloat(p);
            float y = MemoryUtil.memGetFloat(p + 4);
            float z = MemoryUtil.memGetFloat(p + 8);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        float[] ndc = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                Float.NEGATIVE_INFINITY};
        boolean ok = true;
        for (int c = 0; c < 8 && ok; c++) {
            ok = project(clipFromVertex, (c & 1) == 0 ? minX : maxX, (c & 2) == 0 ? minY : maxY,
                    (c & 4) == 0 ? minZ : maxZ, ndc);
        }
        if (!ok) {
            ndc[0] = Float.POSITIVE_INFINITY;
            ndc[1] = Float.POSITIVE_INFINITY;
            ndc[2] = Float.NEGATIVE_INFINITY;
            ndc[3] = Float.NEGATIVE_INFINITY;
            p = address + positionOffset;
            for (int i = 0; i < vertexCount; i++, p += stride) {
                if (!project(clipFromVertex, MemoryUtil.memGetFloat(p), MemoryUtil.memGetFloat(p + 4),
                        MemoryUtil.memGetFloat(p + 8), ndc)) {
                    unbounded = true;
                    return;
                }
            }
        }
        if (!(ndc[0] <= ndc[2]) || !(ndc[1] <= ndc[3])) {
            // NaN positions: cannot be bounded. / 位置为 NaN：无法求界。
            unbounded = true;
            return;
        }
        int x0 = (int) Math.floor((Math.max(-1.0F, ndc[0]) * 0.5F + 0.5F) * width) - MARGIN;
        int y0 = (int) Math.floor((Math.max(-1.0F, ndc[1]) * 0.5F + 0.5F) * height) - MARGIN;
        int x1 = (int) Math.ceil((Math.min(1.0F, ndc[2]) * 0.5F + 0.5F) * width) + MARGIN;
        int y1 = (int) Math.ceil((Math.min(1.0F, ndc[3]) * 0.5F + 0.5F) * height) + MARGIN;
        x0 = Math.max(0, x0);
        y0 = Math.max(0, y0);
        x1 = Math.min(width, x1);
        y1 = Math.min(height, y1);
        if (x1 > x0 && y1 > y0) {
            add(new int[]{x0, y0, x1, y1});
        }
    }

    /**
     * Adds a pixel rectangle, merging so at most {@link #MAX_REGIONS} remain.
     * 加入一个像素矩形，合并后至多保留 {@link #MAX_REGIONS} 个。
     */
    void add(int[] rect) {
        int[] merged = rect;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < regions.size(); i++) {
                if (touches(regions.get(i), merged)) {
                    merged = union(regions.remove(i), merged);
                    changed = true;
                    break;
                }
            }
        }
        regions.add(merged);
        while (regions.size() > MAX_REGIONS) {
            // Merge the pair whose union adds the least area. / 合并并集面积增加最少的一对。
            int bestA = 0;
            int bestB = 1;
            long best = Long.MAX_VALUE;
            for (int a = 0; a < regions.size(); a++) {
                for (int b = a + 1; b < regions.size(); b++) {
                    long growth = area(union(regions.get(a), regions.get(b)))
                            - area(regions.get(a)) - area(regions.get(b));
                    if (growth < best) {
                        best = growth;
                        bestA = a;
                        bestB = b;
                    }
                }
            }
            int[] b = regions.remove(bestB);
            int[] a = regions.remove(bestA);
            add(union(a, b));
        }
    }

    private static boolean project(Matrix4f m, float x, float y, float z, float[] ndc) {
        float w = m.m03() * x + m.m13() * y + m.m23() * z + m.m33();
        if (!(w > MIN_W)) {
            return false;
        }
        float cx = (m.m00() * x + m.m10() * y + m.m20() * z + m.m30()) / w;
        float cy = (m.m01() * x + m.m11() * y + m.m21() * z + m.m31()) / w;
        ndc[0] = Math.min(ndc[0], cx);
        ndc[1] = Math.min(ndc[1], cy);
        ndc[2] = Math.max(ndc[2], cx);
        ndc[3] = Math.max(ndc[3], cy);
        return true;
    }

    private static boolean touches(int[] a, int[] b) {
        return a[0] <= b[2] && b[0] <= a[2] && a[1] <= b[3] && b[1] <= a[3];
    }

    private static int[] union(int[] a, int[] b) {
        return new int[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[2], b[2]), Math.max(a[3], b[3])};
    }

    private static long area(int[] r) {
        return (long) (r[2] - r[0]) * (r[3] - r[1]);
    }

    /**
     * {@code a} intersected with {@code b}, or null when they do not overlap. / {@code a} 与 {@code b} 的交集；不重叠时为 null。
     */
    public static int[] intersect(int[] a, int[] b) {
        int x0 = Math.max(a[0], b[0]);
        int y0 = Math.max(a[1], b[1]);
        int x1 = Math.min(a[2], b[2]);
        int y1 = Math.min(a[3], b[3]);
        return x1 > x0 && y1 > y0 ? new int[]{x0, y0, x1, y1} : null;
    }
}
