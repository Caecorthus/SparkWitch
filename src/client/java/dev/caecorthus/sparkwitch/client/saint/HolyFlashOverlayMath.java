package dev.caecorthus.sparkwitch.client.saint;

import dev.caecorthus.sparkwitch.roles.civilian.saint.flash.HolyFlashRules;

/**
 * Pure screen math for the Holy Flash overlay (owner pick B): world-to-screen projection from a camera basis, the
 * bright-spot anchor (projected burst when faced, nearest screen edge when not), glow radius and alpha over the
 * {@link HolyFlashRules#SPOT_TICKS} spot, the short white-to-black residue and the faint retinal afterimage. No
 * Minecraft client types, so every curve is unit-testable; the black mask itself is
 * {@link HolyFlashRules#blackness(float, int)}.
 * 圣光弹遮罩的纯屏幕数学（所有者选择方案 B）：基于相机基向量的世界到屏幕投影、亮点锚点（正对时为爆点投影位置，
 * 背对时为最近的屏幕边缘）、{@link HolyFlashRules#SPOT_TICKS} 亮点阶段内光晕的半径与透明度、短暂的白转黑残留，
 * 以及淡淡的视网膜残像。不依赖任何 Minecraft 客户端类型，因此所有曲线都可单元测试；黑色遮罩本身由
 * {@link HolyFlashRules#blackness(float, int)} 决定。
 */
public final class HolyFlashOverlayMath {
    /** Glow texture radius fraction that is still fully opaque (see holy_flash_glow.png). / 光晕贴图仍完全不透明的半径比例。 */
    public static final float GLOW_OPAQUE_RADIUS_FRACTION = 0.32F;
    /** End radius = farthest-corner distance / opaque fraction, so the whole screen is covered. / 终止半径保证铺满全屏。 */
    public static final float COVER_OVERSCAN = 1.0F / GLOW_OPAQUE_RADIUS_FRACTION;
    public static final float FACED_START_RADIUS_FRACTION = 0.06F;
    public static final float EDGE_START_RADIUS_FRACTION = 0.45F;
    /** Edge glow centre sits this fraction of the short side beyond the border. / 边缘光晕中心位于边框外短边的该比例处。 */
    public static final float EDGE_OUTSET_FRACTION = 0.15F;
    public static final float FACED_GLOW_ALPHA = 1.0F;
    public static final float AWAY_GLOW_ALPHA = 0.55F;
    /** White residue fading into the black mask right after the spot. / 亮点之后淡入黑幕的白色残留。 */
    public static final float WHITEOUT_TICKS = 4.0F;
    public static final float AFTERIMAGE_TICKS = 30.0F;
    public static final float AFTERIMAGE_PEAK_ALPHA = 0.28F;
    public static final float AFTERIMAGE_START_RADIUS_FRACTION = 0.14F;
    public static final float AFTERIMAGE_END_RADIUS_FRACTION = 0.09F;
    private static final double MIN_DEPTH = 0.05D;
    private static final double MIN_DIRECTION = 1.0E-4D;

    private HolyFlashOverlayMath() {
    }

    /**
     * Camera-space burst position plus its screen point. {@code depth <= 0} means behind the camera, where
     * {@code screenX/Y} are meaningless; {@code right/up} still give the lateral direction.
     * 爆点在相机空间的坐标及其屏幕位置。{@code depth <= 0} 表示在相机背后，此时屏幕坐标无意义，
     * 但 {@code right/up} 仍给出横向方向。
     */
    public record Projection(double right, double up, double depth, double screenX, double screenY) {
        public boolean inFront() {
            return depth > MIN_DEPTH;
        }

        public boolean onScreen(double width, double height) {
            return inFront() && screenX >= 0.0D && screenX <= width && screenY >= 0.0D && screenY <= height;
        }
    }

    /** Glow centre in scaled GUI pixels; {@code dim} means the weaker, not-faced flash. / 光晕中心；dim 表示较弱的未正对闪光。 */
    public record Anchor(double x, double y, boolean dim) {
    }

    /**
     * Projects {@code (dx, dy, dz)} (burst minus camera position) with an orthonormal camera basis and a vertical
     * field of view, matching vanilla's perspective matrix.
     * 用正交相机基向量与竖直视场角投影 {@code (dx, dy, dz)}（爆点减相机位置），与原版透视矩阵一致。
     */
    public static Projection project(double dx, double dy, double dz,
                                     double forwardX, double forwardY, double forwardZ,
                                     double upX, double upY, double upZ,
                                     double rightX, double rightY, double rightZ,
                                     double fovYDegrees, double width, double height) {
        double right = dx * rightX + dy * rightY + dz * rightZ;
        double up = dx * upX + dy * upY + dz * upZ;
        double depth = dx * forwardX + dy * forwardY + dz * forwardZ;
        if (depth <= MIN_DEPTH || width <= 0.0D || height <= 0.0D) {
            return new Projection(right, up, depth, Double.NaN, Double.NaN);
        }
        double tanHalf = Math.tan(Math.toRadians(clamp(fovYDegrees, 1.0D, 179.0D)) * 0.5D);
        double aspect = width / height;
        double ndcX = right / (depth * tanHalf * aspect);
        double ndcY = up / (depth * tanHalf);
        return new Projection(right, up, depth, width * 0.5D * (1.0D + ndcX), height * 0.5D * (1.0D - ndcY));
    }

    /**
     * Faced and on screen: the projected burst. Otherwise the glow enters from the edge nearest the burst direction
     * and is dim. A null projection (no usable camera, e.g. the Seeker remote view) centres the glow.
     * 正对且在屏幕内：使用爆点投影位置。否则光晕从最接近爆点方向的屏幕边缘进入且较弱。投影为 null（无可用相机，
     * 例如搜寻者遥控视角）时光晕居中。
     */
    public static Anchor anchor(boolean faced, Projection projection, double width, double height) {
        double cx = width * 0.5D;
        double cy = height * 0.5D;
        if (projection == null) {
            return new Anchor(cx, cy, !faced);
        }
        if (faced && projection.onScreen(width, height)) {
            return new Anchor(projection.screenX(), projection.screenY(), false);
        }
        // Screen y grows downward, camera up grows upward. / 屏幕 y 向下增长，相机 up 向上增长。
        double dirX = projection.right();
        double dirY = -projection.up();
        double length = Math.hypot(dirX, dirY);
        if (length < MIN_DIRECTION) {
            return new Anchor(cx, cy, true);
        }
        double outset = Math.min(width, height) * EDGE_OUTSET_FRACTION;
        return edgeAnchor(dirX / length, dirY / length, width, height, outset);
    }

    /** Where a ray from the screen centre along a unit direction leaves the screen, pushed out by {@code outset}. */
    static Anchor edgeAnchor(double unitX, double unitY, double width, double height, double outset) {
        double halfW = width * 0.5D;
        double halfH = height * 0.5D;
        double tx = Math.abs(unitX) < MIN_DIRECTION ? Double.POSITIVE_INFINITY : halfW / Math.abs(unitX);
        double ty = Math.abs(unitY) < MIN_DIRECTION ? Double.POSITIVE_INFINITY : halfH / Math.abs(unitY);
        double t = Math.min(tx, ty) + outset;
        return new Anchor(halfW + unitX * t, halfH + unitY * t, true);
    }

    /** 0..1 progress through the bright spot. / 亮点阶段的 0..1 进度。 */
    public static float spotProgress(float elapsedTicks) {
        return clamp(elapsedTicks / HolyFlashRules.SPOT_TICKS, 0.0F, 1.0F);
    }

    public static boolean inSpotPhase(float elapsedTicks) {
        return elapsedTicks < HolyFlashRules.SPOT_TICKS;
    }

    /** Distance from a point to the farthest screen corner. / 点到最远屏幕角的距离。 */
    public static double coverDistance(double x, double y, double width, double height) {
        double farX = Math.max(Math.abs(x), Math.abs(width - x));
        double farY = Math.max(Math.abs(y), Math.abs(height - y));
        return Math.hypot(farX, farY);
    }

    /** Rapid ease-out from a small spot (or a wide edge bloom) to a radius that whites out the screen. */
    public static double glowRadius(float progress, Anchor anchor, double width, double height) {
        double shortSide = Math.min(width, height);
        double start = shortSide * (anchor.dim() ? EDGE_START_RADIUS_FRACTION : FACED_START_RADIUS_FRACTION);
        double end = coverDistance(anchor.x(), anchor.y(), width, height) * COVER_OVERSCAN;
        return start + (end - start) * easeOutCubic(clamp(progress, 0.0F, 1.0F));
    }

    public static float glowAlpha(boolean dim) {
        return dim ? AWAY_GLOW_ALPHA : FACED_GLOW_ALPHA;
    }

    /** White residue over the black mask right after the spot, so white never hard-cuts to black. */
    public static float whiteout(float elapsedTicks, boolean dim) {
        float t = (elapsedTicks - HolyFlashRules.SPOT_TICKS) / WHITEOUT_TICKS;
        if (t < 0.0F || t >= 1.0F) {
            return 0.0F;
        }
        float rest = 1.0F - t;
        return glowAlpha(dim) * rest * rest;
    }

    /** Afterimage window: never past the black hold. / 残像窗口：不超过全黑保持阶段。 */
    public static float afterimageWindow(int totalTicks) {
        float holdEnd = totalTicks * HolyFlashRules.BLACK_HOLD_FRACTION;
        return Math.min(AFTERIMAGE_TICKS, holdEnd - HolyFlashRules.SPOT_TICKS);
    }

    /** Faint retinal burn at the burst spot, faced flashes only, fading fast. / 仅正对时出现的淡淡视网膜残像，快速消退。 */
    public static float afterimageAlpha(float elapsedTicks, int totalTicks, boolean dim) {
        if (dim) {
            return 0.0F;
        }
        float window = afterimageWindow(totalTicks);
        if (window <= 0.0F) {
            return 0.0F;
        }
        float t = (elapsedTicks - HolyFlashRules.SPOT_TICKS) / window;
        if (t < 0.0F || t >= 1.0F) {
            return 0.0F;
        }
        float rest = 1.0F - t;
        return AFTERIMAGE_PEAK_ALPHA * rest * rest;
    }

    public static double afterimageRadius(float elapsedTicks, int totalTicks, double width, double height) {
        float window = afterimageWindow(totalTicks);
        float t = window <= 0.0F ? 1.0F
                : clamp((elapsedTicks - HolyFlashRules.SPOT_TICKS) / window, 0.0F, 1.0F);
        double fraction = AFTERIMAGE_START_RADIUS_FRACTION
                + (AFTERIMAGE_END_RADIUS_FRACTION - AFTERIMAGE_START_RADIUS_FRACTION) * t;
        return Math.min(width, height) * fraction;
    }

    /** Packs an alpha (0..1) and an RGB colour into ARGB. / 将 0..1 透明度与 RGB 合成 ARGB。 */
    public static int argb(float alpha, int rgb) {
        int a = Math.round(clamp(alpha, 0.0F, 1.0F) * 255.0F);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    static float easeOutCubic(float t) {
        float rest = 1.0F - t;
        return 1.0F - rest * rest * rest;
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }
}
