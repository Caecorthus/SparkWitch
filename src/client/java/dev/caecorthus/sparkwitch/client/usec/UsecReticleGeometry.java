package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecBallistics;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRules;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometry of the USEC reticles (no rendering state), matching the WP6 spec sheet
 * ({@code usec-art/ui/README.md}, "Reticles"). Angles become framebuffer pixels through the frame's real projection:
 * {@code m} mrad sits {@code ppt * tan(m / 1000)} px from the lens centre, with {@code ppt = (H / 2) / tan(fov / 2)},
 * so 1 mrad is 3.508 px at 4x and 7.058 px at 8x on a 1080p, 70-degree frame. AP holdover marks are
 * {@code atan(apDropAt(distance, marksman) / distance)} from {@link UsecBallistics} (the server tracer's own math).
 * Stroke widths, minimum mark sizes and label texels are fixed 1080p pixels times {@code round(H / 1080)}.
 * Coordinates are framebuffer pixels relative to the lens centre, y down; the wire column and row are pixel 0, so
 * a mark {@code n} px right sits in column {@code n} and its mirror in column {@code -n}.
 * USEC 分划的纯几何（无渲染状态），与 WP6 规格表（{@code usec-art/ui/README.md}“Reticles”）一致。角度经本帧真实投影换算为
 * 帧缓冲像素：{@code m} 密位距镜片中心 {@code ppt * tan(m / 1000)} 像素，{@code ppt = (H / 2) / tan(fov / 2)}，因此在 1080p、
 * 70 度画面上 4 倍时 1 密位为 3.508 像素、8 倍时为 7.058 像素。AP 抬枪刻度取自 {@link UsecBallistics}（服务端射线追踪自己的
 * 数学）：{@code atan(apDropAt(距离, 精确枪手) / 距离)}。线宽、刻度最小尺寸与标签像素为 1080p 下的固定像素乘以
 * {@code round(H / 1080)}。坐标为相对镜片中心的帧缓冲像素，y 轴向下；十字线所在的列与行为第 0 像素，因此右侧 {@code n}
 * 像素处的刻度位于第 {@code n} 列，其镜像位于第 {@code -n} 列。
 */
public final class UsecReticleGeometry {
    /** AP holdover ladder distances in blocks (D14). / AP 抬枪梯的距离（格，D14）。 */
    public static final List<Integer> AP_HOLDOVER_DISTANCES = List.of(100, 125, 150, 175, 200);
    /** A standing player's height in blocks, the ranging bracket target. / 站立玩家的高度（格），即测距括号目标。 */
    public static final double PERSON_HEIGHT_BLOCKS = 1.8;

    // ---- R2 tactical MRAD tree (first focal plane; every position in mrad) ----
    public static final double R2_OPEN_CENTRE_MRAD = 1.0;
    public static final double R2_WIRE_END_MRAD = 12.0;
    public static final double R2_TRUNK_END_MRAD = 16.0;
    public static final int R2_HASH_EXTENT_MRAD = 10;
    public static final int R2_TRUNK_HASHES = 16;
    public static final double R2_MINOR_HALF_MRAD = 0.35;
    public static final double R2_MAJOR_HALF_MRAD = 0.75;
    public static final double R2_POST_START_MRAD = 12.0;
    public static final double R2_BOTTOM_POST_START_MRAD = 38.0;
    public static final int R2_POST_PX = 3;
    /** Ladder labels sit in fixed columns just outside the widest (200) bar. / 标签固定在最宽（200）横杠外侧。 */
    public static final double R2_LABEL_COLUMN_MRAD = 2.2;
    public static final double R2_RANGING_BASE_MRAD = 30.0;
    /** Bracket columns (mrad left of centre) for {@link #AP_HOLDOVER_DISTANCES}. / 各距离括号所在列（中心左侧密位）。 */
    public static final List<Double> R2_RANGING_X_MRAD = List.of(8.0, 15.0, 22.0, 29.0, 36.0);
    public static final double R2_RANGING_MARGIN_MRAD = 2.5;
    public static final double R2_SERIF_HALF_MRAD = 0.8;
    /** 4 x 8 legend glyph: "a bracket is one player tall". / 4 x 8 图例小人：“括号高度为一名玩家”。 */
    public static final List<String> PERSON_GLYPH = List.of(".##.", ".##.", "####", ".##.", ".##.", ".##.", ".#.#",
            ".#.#");

    // ---- R1 classic Mil-Dot (second focal plane, true at 8x; every position in mil) ----
    public static final int R1_DOTS_SIDE = 4;
    public static final int R1_DOTS_DOWN = 14;
    public static final double R1_POST_START_MIL = 5.0;
    public static final double R1_LOWER_POST_START_MIL = 15.0;
    public static final int R1_POST_PX = 4;
    /** R1 is calibrated at this zoom level (8x). / R1 在该倍率档位（8 倍）下校准。 */
    public static final int R1_CALIBRATION_LEVEL = 1;

    /** Digit glyph height in font texels. / 数字字形高度（字体像素）。 */
    public static final int DIGIT_HEIGHT = 7;

    /** Etched wire {@code #101010} at 93%. / 蚀刻线 {@code #101010}，不透明度 93%。 */
    public static final int ETCH_COLOR = 0xED101010;
    /** 1 px light halo {@code #ECE8DC} at 20% around every stroke. / 每条线外 1 像素浅色光晕。 */
    public static final int HALO_COLOR = 0x33ECE8DC;
    /** Illuminated parts: AP red lifted for emission. / 发光部件：提亮的 AP 红。 */
    public static final int ILLUM_COLOR = 0xFFEC4A3C;
    /** 1 px dark keyline around illuminated parts. / 发光部件外 1 像素深色描边。 */
    public static final int KEYLINE_COLOR = 0xB3140806;
    /** Faint glow around illuminated parts. / 发光部件外的微弱辉光。 */
    public static final int GLOW_COLOR = 0x24EC4A3C;

    private UsecReticleGeometry() {
    }

    /**
     * Pixels per unit of tangent: half the frame height over {@code tan(fov / 2)}; NaN for an unusable FOV or height.
     * 每单位正切对应的像素：画面半高除以 {@code tan(fov / 2)}；视野或高度不可用时为 NaN。
     */
    public static double pixelsPerTangent(double projectionFovDegrees, double heightPx) {
        if (!(projectionFovDegrees > 0.0 && projectionFovDegrees < 180.0) || !(heightPx > 0.0)
                || !Double.isFinite(heightPx)) {
            return Double.NaN;
        }
        return heightPx / 2.0 / Math.tan(Math.toRadians(projectionFovDegrees) / 2.0);
    }

    public static double mradToPixels(double mrad, double pixelsPerTangent) {
        return pixelsPerTangent * Math.tan(mrad / 1000.0);
    }

    public static double pixelsToMrad(double pixels, double pixelsPerTangent) {
        return Math.atan(pixels / pixelsPerTangent) * 1000.0;
    }

    /**
     * Angle in mrad below the line of sight at which an AP round crosses {@code distance} blocks with no obstacle:
     * {@code atan(apDropAt(distance, marksman) / distance)}; 0 for a non-positive distance.
     * 无障碍时 AP 子弹飞过 {@code distance} 格时位于视线下方的角度（密位）；距离不为正时为 0。
     */
    public static double apHoldoverMrad(double distance, double marksmanMultiplier) {
        if (!(distance > 0.0) || !Double.isFinite(distance)) {
            return 0.0;
        }
        return Math.atan2(UsecBallistics.apDropAt(distance, marksmanMultiplier), distance) * 1000.0;
    }

    /** Spec bracket height: 1.8 / d in mrad (18.0 at 100). / 规格括号高度：1.8 / d 密位（100 格为 18.0）。 */
    public static double personHeightMrad(double distance) {
        return PERSON_HEIGHT_BLOCKS / distance * 1000.0;
    }

    /** Ladder bar half width: 0.80 mrad at 100 up to 2.20 at 200. / 抬枪横杠半宽：100 格 0.80 到 200 格 2.20。 */
    public static double ladderHalfWidthMrad(int distance) {
        return 0.8 + 1.4 * (distance - 100) / 100.0;
    }

    /** Stroke scale: {@code round(H / 1080)}, at least 1. / 线宽倍数：{@code round(H / 1080)}，至少为 1。 */
    public static int strokeScale(double heightPx) {
        return Double.isFinite(heightPx) ? Math.max(1, (int) Math.round(heightPx / 1080.0)) : 1;
    }

    /**
     * The whole reticle for one frame. {@code projectionFovDegrees} is the vertical FOV this frame was projected with
     * and {@code zoomFovMultiplier} the scope multiplier inside it (R1 recovers its 8x calibration from the two).
     * {@code heightPx} and {@code lensRadiusPx} are framebuffer pixels. An unusable FOV yields nothing.
     * 一帧的完整分划。{@code projectionFovDegrees} 为本帧实际投影使用的竖直视野，{@code zoomFovMultiplier} 为其中包含的
     * 开镜倍率（R1 由二者还原 8 倍校准）。{@code heightPx} 与 {@code lensRadiusPx} 为帧缓冲像素。视野不可用时不画任何东西。
     */
    public static Layout layout(UsecReticleStyle style, double projectionFovDegrees, double zoomFovMultiplier,
                                double heightPx, double lensRadiusPx, double marksmanMultiplier) {
        Builder out = new Builder((int) Math.floor(lensRadiusPx), strokeScale(heightPx));
        double ppt = pixelsPerTangent(projectionFovDegrees, heightPx);
        if (style == null || out.radius < 4 || !(ppt > 0.0) || !Double.isFinite(ppt)) {
            return out.build();
        }
        if (style == UsecReticleStyle.MIL_DOT) {
            double calibration = UsecRules.zoomFovMultiplier(R1_CALIBRATION_LEVEL);
            double multiplier = zoomFovMultiplier > 0.0 && Double.isFinite(zoomFovMultiplier)
                    ? zoomFovMultiplier : calibration;
            double calibrated = pixelsPerTangent(projectionFovDegrees * calibration / multiplier, heightPx);
            milDot(out, Double.isFinite(calibrated) ? mradToPixels(1.0, calibrated) : mradToPixels(1.0, ppt));
        } else {
            tacticalTree(out, ppt, marksmanMultiplier);
        }
        return out.build();
    }

    /** R2: first focal plane, so every position scales with the frame's projection. / R2：第一焦平面，位置随投影缩放。 */
    private static void tacticalTree(Builder out, double ppt, double marksman) {
        int s = out.scale;
        int r = out.radius;
        double pxPerMrad = mradToPixels(1.0, ppt);
        int gap = px(ppt, R2_OPEN_CENTRE_MRAD);
        int wire = Math.min(r, px(ppt, R2_WIRE_END_MRAD));
        int trunk = Math.min(r, px(ppt, R2_TRUNK_END_MRAD));

        // Fine crosshair with an open centre; the trunk runs on to 16. / 中心镂空的细十字线；下方主干延伸到 16。
        // Mirrors use [s - b, s - a) so both arms sit symmetric about the s-px wire. / 镜像使用 [s - b, s - a)。
        out.etch(gap, 0, wire, s);
        out.etch(s - wire, 0, s - gap, s);
        out.etch(0, s - wire, s, s - gap);
        out.etch(0, gap, s, trunk);

        // Posts from 12 (sides, top) and 38 (bottom) to the lens edge. / 粗柱：两侧与上方自 12，下方自 38，到镜片边缘。
        int post = R2_POST_PX * s;
        int low = Math.floorDiv(s - post, 2);
        int side = px(ppt, R2_POST_START_MRAD);
        if (side < r) {
            out.etch(side, low, r + 1, low + post);
            out.etch(-r, low, s - side, low + post);
            out.etch(low, -r, low + post, s - side);
        }
        int bottom = px(ppt, R2_BOTTOM_POST_START_MRAD);
        if (bottom < r) {
            out.etch(low, bottom, low + post, r + 1);
        }

        // 1-mrad hashes to 10 on the sides and top; left-only hashes down the trunk to 16.
        // 两侧与上方每 1 密位刻度到 10；主干只在左侧刻度，到 16。
        for (int mrad = 1; mrad <= R2_HASH_EXTENT_MRAD; mrad++) {
            boolean major = mrad % 5 == 0;
            int half = Math.max((major ? 4 : 2) * s, px(ppt, major ? R2_MAJOR_HALF_MRAD : R2_MINOR_HALF_MRAD));
            int at = px(ppt, mrad);
            if (at < side) {
                out.etch(at, -half, at + s, half + 1);
                out.etch(-at, -half, -at + s, half + 1);
                out.etch(-half, -at, half + 1, -at + s);
            }
        }
        for (int mrad = 1; mrad <= R2_TRUNK_HASHES; mrad++) {
            boolean major = mrad % 5 == 0;
            int half = Math.max((major ? 3 : 2) * s, px(ppt, major ? R2_MAJOR_HALF_MRAD : R2_MINOR_HALF_MRAD));
            int at = px(ppt, mrad);
            if (at < trunk) {
                out.etch(-half, at, 0, at + s);
            }
        }

        // Illuminated centre dot: 3 px below 5 px/mrad (4x), else 4 px; corners at 40%.
        // 发光中心点：每密位不足 5 像素（4 倍）时 3 像素，否则 4 像素；四角 40%。
        int dot = (pxPerMrad < 5.0 ? 3 : 4) * s;
        int d0 = Math.floorDiv(s - dot, 2);
        out.illum(d0 + s, d0, d0 + dot - s, d0 + dot, ILLUM_COLOR);
        out.illum(d0, d0 + s, d0 + s, d0 + dot - s, ILLUM_COLOR);
        out.illum(d0 + dot - s, d0 + s, d0 + dot, d0 + dot - s, ILLUM_COLOR);
        int corner = (ILLUM_COLOR & 0x00FFFFFF) | 0x66000000;
        for (int[] c : new int[][]{{d0, d0}, {d0 + dot - s, d0}, {d0, d0 + dot - s}, {d0 + dot - s, d0 + dot - s}}) {
            out.illum(c[0], c[1], c[0] + s, c[1] + s, corner);
        }

        addLadder(out, ppt, marksman, gap, trunk);
        addRanging(out, ppt);
    }

    /**
     * AP ladder (illuminated): a bar per distance at its holdover, half width {@link #ladderHalfWidthMrad} (min 3 px),
     * labels alternating right, left, right... in fixed columns. A hold inside the open centre needs no bar (Marksman
     * at 100); a label that would touch the previous one on its side is dropped (low resolutions only).
     * AP 抬枪梯（发光）：每个距离在其抬枪角处一条横杠，半宽 {@link #ladderHalfWidthMrad}（至少 3 像素），标签在固定列中
     * 右、左、右……交替。落在中心镂空内的抬枪无需横杠（精确枪手下的 100 格）；会碰到同侧上一个标签的标签被省略（仅在低分辨率）。
     */
    private static void addLadder(Builder out, double ppt, double marksman, int gap, int trunk) {
        int s = out.scale;
        int digits = DIGIT_HEIGHT * s;
        int[] lastBottom = {Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int index = 0; index < AP_HOLDOVER_DISTANCES.size(); index++) {
            int distance = AP_HOLDOVER_DISTANCES.get(index);
            double mrad = apHoldoverMrad(distance, marksman);
            int y = px(ppt, mrad);
            if (mrad < R2_OPEN_CENTRE_MRAD || y >= trunk) {
                continue;
            }
            int half = Math.max(3 * s, px(ppt, ladderHalfWidthMrad(distance)));
            out.illum(-half, y, half + 1, y + s, ILLUM_COLOR);
            boolean right = index % 2 == 0;
            int column = Math.max(half, px(ppt, R2_LABEL_COLUMN_MRAD)) + 4 * s;
            int top = y - 3 * s;
            int side = right ? 0 : 1;
            boolean labelled = top > lastBottom[side];
            if (labelled) {
                lastBottom[side] = top + digits;
                out.labels.add(new Label(Integer.toString(distance), right ? column : -column, top, s, Layer.ILLUM,
                        right ? Anchor.LEFT : Anchor.RIGHT));
            }
            out.holdovers.add(new HoldoverMark(distance, mrad, y, labelled, right));
        }
    }

    /**
     * PSO-style person-height brackets: feet on a baseline 30 mrad below centre, one upright per distance whose height
     * is a standing player at that range, a top serif, the range underneath and a 4 x 8 legend glyph at the right end.
     * Pieces that would leave the lens are skipped.
     * PSO 式人形高度括号：脚部位于中心下方 30 密位的基线上，每个距离一根竖线，高度为该距离下的站立玩家，顶部有短横，
     * 下方标注距离，右端有 4 x 8 图例小人。会超出镜片的部分被跳过。
     */
    private static void addRanging(Builder out, double ppt) {
        int s = out.scale;
        int base = px(ppt, R2_RANGING_BASE_MRAD);
        double farthest = R2_RANGING_X_MRAD.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double nearest = R2_RANGING_X_MRAD.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        int left = -px(ppt, farthest + R2_RANGING_MARGIN_MRAD);
        int right = -px(ppt, nearest - R2_RANGING_MARGIN_MRAD);
        int labelBottom = base + 3 * s + DIGIT_HEIGHT * s;
        if (!out.inside(left, labelBottom) || !out.inside(right + 4 * s + 4 * s, labelBottom)) {
            return;
        }
        out.etch(left, base, right + 1, base + s);
        int serif = Math.max(2 * s, px(ppt, R2_SERIF_HALF_MRAD));
        int lastLabelLeft = Integer.MAX_VALUE;
        for (int index = 0; index < AP_HOLDOVER_DISTANCES.size(); index++) {
            int distance = AP_HOLDOVER_DISTANCES.get(index);
            int x = -px(ppt, R2_RANGING_X_MRAD.get(index));
            int top = px(ppt, R2_RANGING_BASE_MRAD - personHeightMrad(distance));
            out.etch(x, top, x + s, base);
            out.etch(x - serif, top, x + serif + 1, top + s);
            // Labels march left from the nearest bracket; one that would touch its neighbour is dropped.
            // 标签自最近的括号向左排列；会碰到相邻标签的标签被省略。
            String text = Integer.toString(distance);
            int width = approximateDigitWidth(text, s);
            if (x + (width + 1) / 2 < lastLabelLeft - s) {
                out.labels.add(new Label(text, x, base + 3 * s, s, Layer.ETCH, Anchor.CENTER));
                lastLabelLeft = x - width / 2;
            }
        }
        int glyphX = right + 4 * s;
        int glyphY = base - (PERSON_GLYPH.size() - 1) * s;
        for (int row = 0; row < PERSON_GLYPH.size(); row++) {
            String line = PERSON_GLYPH.get(row);
            for (int column = 0; column < line.length(); column++) {
                if (line.charAt(column) == '#') {
                    int gx = glyphX + column * s;
                    int gy = glyphY + row * s;
                    out.etch(gx, gy, gx + s, gy + s);
                }
            }
        }
    }

    /**
     * R1: second focal plane, so the mil spacing is the 8x one at every zoom (one mil-dot covers 2.01 mrad at 4x). No
     * labels and no ladder: AP holds are read off the dots (8x: 2.5 / 4 / 7.5 / 10 / 13.75 dots).
     * R1：第二焦平面，因此在任何倍率下密位间距都按 8 倍计算（4 倍时一个密位点覆盖 2.01 密位）。无标签、无抬枪梯：AP 抬枪
     * 按点读取（8 倍：2.5 / 4 / 7.5 / 10 / 13.75 点）。
     */
    private static void milDot(Builder out, double pxPerMil) {
        int s = out.scale;
        int r = out.radius;
        int five = Math.min(r, (int) Math.round(R1_POST_START_MIL * pxPerMil));
        int fifteen = Math.min(r, (int) Math.round(R1_LOWER_POST_START_MIL * pxPerMil));
        // Continuous 1 px wire through the centre. / 穿过中心的连续 1 像素细线。
        out.etch(s - five, 0, five, s);
        out.etch(0, s - five, s, fifteen);
        int post = R1_POST_PX * s;
        int low = Math.floorDiv(s - post, 2);
        if (five < r) {
            out.etch(five, low, r + 1, low + post);
            out.etch(-r, low, s - five, low + post);
            out.etch(low, -r, low + post, s - five);
        }
        if (fifteen < r) {
            out.etch(low, fifteen, low + post, r + 1);
        }
        for (int mil = 1; mil <= R1_DOTS_SIDE; mil++) {
            int at = (int) Math.round(mil * pxPerMil);
            disk(out, at, 0, false);
            disk(out, -at, 0, false);
            disk(out, 0, -at, false);
        }
        for (int mil = 1; mil <= R1_DOTS_DOWN; mil++) {
            int at = (int) Math.round(mil * pxPerMil);
            if (at < fifteen) {
                disk(out, 0, at, mil % 5 == 0);
            }
        }
    }

    /** Round dot: 3 x 3 core plus 1 px caps (5 px across), or 5 x 5 + caps (7 px) when big. / 圆点。 */
    private static void disk(Builder out, int x, int y, boolean big) {
        int s = out.scale;
        int c = (big ? 2 : 1) * s;
        out.etch(x - c, y - c, x + c + s, y + c + s);
        out.etch(x - c - s, y - c + s, x + c + 2 * s, y + c);
        out.etch(x - c + s, y - c - s, x + c, y + c + 2 * s);
    }

    /** Width of a digit string in the vanilla font: 6 texels per digit minus the trailing gap. / 数字串宽度。 */
    static int approximateDigitWidth(String text, int scale) {
        return text.isEmpty() ? 0 : (6 * text.length() - 1) * scale;
    }

    private static int px(double ppt, double mrad) {
        return (int) Math.round(mradToPixels(mrad, ppt));
    }

    /** Which pass a stroke belongs to. / 线条所属的绘制层。 */
    public enum Layer {
        /** Black etched wire with the light halo. / 带浅色光晕的黑色蚀刻线。 */
        ETCH,
        /** Illuminated red with a dark keyline and glow. / 带深色描边与辉光的发光红。 */
        ILLUM
    }

    /** Filled rectangle [x0, x1) x [y0, y1). / 填充矩形。 */
    public record Rect(int x0, int y0, int x1, int y1, Layer layer, int color) {
    }

    /** Horizontal anchor of a label's x. / 标签 x 坐标的水平锚点。 */
    public enum Anchor {
        LEFT,
        RIGHT,
        CENTER
    }

    /** Small number label; {@code y} is the top, {@code scale} framebuffer px per font texel. / 小号数字标签。 */
    public record Label(String text, int x, int y, int scale, Layer layer, Anchor anchor) {
    }

    /** One drawn AP holdover bar (exposed for tests and tuning). / 一条已绘制的 AP 抬枪横杠（供测试与调参）。 */
    public record HoldoverMark(int distance, double mrad, int offsetPx, boolean labelled, boolean labelRight) {
    }

    /** Draw lists; the renderer draws halos, keylines, etch, glow, illum, then labels. / 绘制列表。 */
    public record Layout(List<Rect> rects, List<Label> labels, List<HoldoverMark> holdovers) {
    }

    private static final class Builder {
        final int radius;
        final int scale;
        final List<Rect> rects = new ArrayList<>();
        final List<Label> labels = new ArrayList<>();
        final List<HoldoverMark> holdovers = new ArrayList<>();

        Builder(int radius, int scale) {
            this.radius = radius;
            this.scale = scale;
        }

        void etch(int x0, int y0, int x1, int y1) {
            add(x0, y0, x1, y1, Layer.ETCH, ETCH_COLOR);
        }

        void illum(int x0, int y0, int x1, int y1, int color) {
            add(x0, y0, x1, y1, Layer.ILLUM, color);
        }

        private void add(int x0, int y0, int x1, int y1, Layer layer, int color) {
            int left = Math.min(x0, x1);
            int right = Math.max(x0, x1);
            int top = Math.min(y0, y1);
            int bottom = Math.max(y0, y1);
            if (left < right && top < bottom) {
                rects.add(new Rect(left, top, right, bottom, layer, color));
            }
        }

        boolean inside(int x, int y) {
            return Math.hypot(x, y) <= radius;
        }

        Layout build() {
            return new Layout(List.copyOf(rects), List.copyOf(labels), List.copyOf(holdovers));
        }
    }
}
