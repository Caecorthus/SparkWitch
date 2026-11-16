package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecBallistics;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRules;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometry of the USEC reticles (no rendering state), matching the WP6 spec sheet
 * ({@code usec-art/ui/README.md}, "Reticles"). Angles become framebuffer pixels through the frame's real projection:
 * {@code m} mrad sits {@code ppt * tan(m / 1000)} px from the lens centre, with {@code ppt = (H / 2) / tan(fov / 2)},
 * so 1 mrad is 0.771 px at 1x, 3.508 px at 4x and 5.286 px at 6x on a 1080p, 70-degree frame. AP holdover marks are
 * {@code atan(apDropAt(distance, marksman) / distance)} from {@link UsecBallistics} (the server tracer's own math).
 * Stroke widths, minimum mark sizes and label texels are fixed 1080p pixels times {@code round(H / 1080)}.
 * Coordinates are framebuffer pixels relative to the lens centre, y down; the wire column and row are pixel 0, so
 * a mark {@code n} px right sits in column {@code n} and its mirror in column {@code -n}.
 * USEC 分划的纯几何（无渲染状态），与 WP6 规格表（{@code usec-art/ui/README.md}“Reticles”）一致。角度经本帧真实投影换算为
 * 帧缓冲像素：{@code m} 密位距镜片中心 {@code ppt * tan(m / 1000)} 像素，{@code ppt = (H / 2) / tan(fov / 2)}，因此在 1080p、
 * 70 度画面上 1 倍时 1 密位为 0.771 像素、4 倍时为 3.508 像素、6 倍时为 5.286 像素。AP 抬枪刻度取自
 * {@link UsecBallistics}（服务端射线追踪自己的数学）：{@code atan(apDropAt(距离, 精确枪手) / 距离)}。线宽、刻度最小
 * 尺寸与标签像素为 1080p 下的固定像素乘以 {@code round(H / 1080)}。坐标为相对镜片中心的帧缓冲像素，y 轴向下；十字线所在
 * 的列与行为第 0 像素，因此右侧 {@code n} 像素处的刻度位于第 {@code n} 列，其镜像位于第 {@code -n} 列。
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

    // ---- R2 low-magnification level of detail (1080p framebuffer px, times the stroke scale) ----
    // The tree is first focal plane, so it shrinks with the zoom while strokes and digits keep their pixel size: at 1x
    // one mrad is under a pixel and every mark would land on its neighbour. A mark is therefore drawn only where it
    // clears the marks around it; whatever is drawn still sits at its true angle.
    // 树形分划位于第一焦平面，随倍率缩小，而线宽与数字保持像素大小：1 倍时 1 密位不足 1 像素，所有刻度都会叠在相邻刻度上。
    // 因此刻度只在不与周围刻度相碰时才绘制；画出的刻度仍位于真实角度。
    /** 1-mrad hashes need this pitch; below it only the 5-mrad majors stay. / 每密位刻度所需间距，不足时只保留 5 密位大刻度。 */
    public static final double R2_MIN_MINOR_PITCH_PX = 2.5;
    /** The 5-mrad majors need this pitch; below it the wire is bare. / 5 密位大刻度所需间距，不足时细线上不画刻度。 */
    public static final double R2_MIN_MAJOR_PITCH_PX = 6.0;
    /** Clear rows a ladder bar keeps below the centre dot. / 抬枪横杠与中心点之间须留出的空行。 */
    public static final int R2_MIN_DOT_GAP_PX = 2;
    /** Pitch a ladder bar keeps from every bar that outranks it. / 抬枪横杠与优先级更高的横杠之间的最小间距。 */
    public static final double R2_MIN_LADDER_PITCH_PX = 3.5;
    /**
     * Order in which the ladder fills in as the tree grows: the far end and the middle first, the in-between holds
     * last. / 分划放大时抬枪梯补全的顺序：先远端与中间，最后是夹在中间的距离。
     */
    public static final List<Integer> R2_LADDER_PRIORITY = List.of(200, 150, 100, 175, 125);
    /** A ladder label may sit this far below its centred place to fit. / 抬枪标签为放得下最多可下移的量。 */
    public static final int R2_LABEL_MAX_LOWER_PX = 2;
    /** The ranging brackets need this column pitch; below it the block is left out. / 测距括号所需列间距，不足时整块不画。 */
    public static final double R2_MIN_RANGING_PITCH_PX = 9.0;

    // ---- R1 classic Mil-Dot (second focal plane, true at the top 6x; every position in mil) ----
    public static final int R1_DOTS_SIDE = 4;
    public static final int R1_DOTS_DOWN = 14;
    public static final double R1_POST_START_MIL = 5.0;
    public static final double R1_LOWER_POST_START_MIL = 15.0;
    public static final int R1_POST_PX = 4;
    /**
     * R1 is calibrated at the top magnification (6x), as second-focal-plane scopes are.
     * R1 在最高倍率（6 倍）下校准，与第二焦平面瞄准镜一致。
     */
    public static final double R1_CALIBRATION_MAGNIFICATION = UsecRules.ZOOM_MAX_MAGNIFICATION;

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

    /**
     * Spacing in mrad of the hashes drawn at {@code pxPerMrad}: 1 (every hash), 5 (majors only) or 0 (none), by
     * {@link #R2_MIN_MINOR_PITCH_PX} and {@link #R2_MIN_MAJOR_PITCH_PX}. At 1080p and 70 degrees that is every hash
     * from about 3x, majors from about 1.5x and a bare wire at 1x.
     * 在 {@code pxPerMrad} 下绘制的刻度间隔（密位）：1（全部）、5（仅大刻度）或 0（不画）。1080p、70 度下约 3 倍起画全部，
     * 约 1.5 倍起只画大刻度，1 倍时细线上不画刻度。
     */
    public static int hashStepMrad(double pxPerMrad, int strokeScale) {
        int s = Math.max(1, strokeScale);
        if (pxPerMrad >= R2_MIN_MINOR_PITCH_PX * s) {
            return 1;
        }
        return 5.0 * pxPerMrad >= R2_MIN_MAJOR_PITCH_PX * s ? 5 : 0;
    }

    /** Stroke scale: {@code round(H / 1080)}, at least 1. / 线宽倍数：{@code round(H / 1080)}，至少为 1。 */
    public static int strokeScale(double heightPx) {
        return Double.isFinite(heightPx) ? Math.max(1, (int) Math.round(heightPx / 1080.0)) : 1;
    }

    /**
     * The whole reticle for one frame. {@code projectionFovDegrees} is the vertical FOV this frame was projected with
     * and {@code zoomFovMultiplier} the scope multiplier inside it (R1 recovers its 6x calibration from the two).
     * {@code heightPx} and {@code lensRadiusPx} are framebuffer pixels. An unusable FOV yields nothing.
     * 一帧的完整分划。{@code projectionFovDegrees} 为本帧实际投影使用的竖直视野，{@code zoomFovMultiplier} 为其中包含的
     * 开镜倍率（R1 由二者还原 6 倍校准）。{@code heightPx} 与 {@code lensRadiusPx} 为帧缓冲像素。视野不可用时不画任何东西。
     */
    public static Layout layout(UsecReticleStyle style, double projectionFovDegrees, double zoomFovMultiplier,
                                double heightPx, double lensRadiusPx, double marksmanMultiplier) {
        Builder out = new Builder((int) Math.floor(lensRadiusPx), strokeScale(heightPx));
        double ppt = pixelsPerTangent(projectionFovDegrees, heightPx);
        if (style == null || out.radius < 4 || !(ppt > 0.0) || !Double.isFinite(ppt)) {
            return out.build();
        }
        if (style == UsecReticleStyle.MIL_DOT) {
            double calibration = 1.0 / R1_CALIBRATION_MAGNIFICATION;
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

        // 1-mrad hashes to 10 on the sides and top and down the trunk to 16, every one across its wire (owner,
        // 2026-11-11: the trunk's used to run left of the line only and read as half missing). Low magnifications keep
        // only the hashes that stay apart (hashStepMrad). / 两侧与上方每 1 密位刻度到 10，主干到 16，每条刻度都横跨所在的线
        // （所有者 2026-11-11：主干刻度原先只在线左侧，看起来像少了一半）。低倍率下只保留互不相碰的刻度（hashStepMrad）。
        int step = hashStepMrad(pxPerMrad, s);
        for (int mrad = 1; mrad <= R2_HASH_EXTENT_MRAD; mrad++) {
            boolean major = mrad % 5 == 0;
            int half = Math.max((major ? 4 : 2) * s, px(ppt, major ? R2_MAJOR_HALF_MRAD : R2_MINOR_HALF_MRAD));
            int at = px(ppt, mrad);
            if (step != 0 && mrad % step == 0 && at < side) {
                out.etch(at, -half, at + s, half + 1);
                out.etch(-at, -half, -at + s, half + 1);
                out.etch(-half, -at, half + 1, -at + s);
            }
        }
        for (int mrad = 1; mrad <= R2_TRUNK_HASHES; mrad++) {
            boolean major = mrad % 5 == 0;
            int half = Math.max((major ? 4 : 2) * s, px(ppt, major ? R2_MAJOR_HALF_MRAD : R2_MINOR_HALF_MRAD));
            int at = px(ppt, mrad);
            if (step != 0 && mrad % step == 0 && at < trunk) {
                out.etch(-half, at, half + 1, at + s);
            }
        }

        // Illuminated centre dot: 3 px below 5 px/mrad (up to about 5.7x at 1080p, 70 degrees), else 4 px; corners 40%.
        // 发光中心点：每密位不足 5 像素（1080p、70 度下约 5.7 倍以下）时 3 像素，否则 4 像素；四角 40%。
        int dot = (pxPerMrad < 5.0 ? 3 : 4) * s;
        int d0 = Math.floorDiv(s - dot, 2);
        out.illum(d0 + s, d0, d0 + dot - s, d0 + dot, ILLUM_COLOR);
        out.illum(d0, d0 + s, d0 + s, d0 + dot - s, ILLUM_COLOR);
        out.illum(d0 + dot - s, d0 + s, d0 + dot, d0 + dot - s, ILLUM_COLOR);
        int corner = (ILLUM_COLOR & 0x00FFFFFF) | 0x66000000;
        for (int[] c : new int[][]{{d0, d0}, {d0 + dot - s, d0}, {d0, d0 + dot - s}, {d0 + dot - s, d0 + dot - s}}) {
            out.illum(c[0], c[1], c[0] + s, c[1] + s, corner);
        }

        // Ladder labels stay below the wire's furniture: the tallest hash drawn, or the side posts on a bare wire.
        // 抬枪标签位于十字线附属物下方：画出的最长刻度；细线上没有刻度时则为两侧粗柱。
        out.keepOut(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
                step != 0 ? Math.max(4 * s, px(ppt, R2_MAJOR_HALF_MRAD)) + 1 : low + post);
        // The brackets go first so the ladder labels can keep off them. / 先画括号，抬枪标签才能避开它们。
        addRanging(out, ppt);
        addLadder(out, ppt, marksman, d0 + dot);
    }

    /**
     * {@link #px} before it is floored ({@code floor(exact) == px}). Label placement compares these instead of pixels:
     * two rounded positions that both scale with the zoom would trade a pixel back and forth and make a label blink
     * while the zoom eases; a placement that clears by unrounded positions also clears in pixels.
     * 取整前的 {@link #px}（{@code floor(exact) == px}）。标签摆放比较这些值而不是像素：两个都随倍率缩放的取整位置会来回
     * 差一个像素，使标签在倍率缓动时闪烁；按未取整位置不相碰的摆放，在像素上也不相碰。
     */
    private static double exact(double ppt, double mrad) {
        return mradToPixels(mrad, ppt) + 0.5;
    }

    /**
     * AP ladder (illuminated): a bar per distance at its holdover, half width {@link #ladderHalfWidthMrad} (min 3 px),
     * labels in fixed columns, alternating right, left, right... by distance. A hold inside the open centre needs no
     * bar (Marksman at 100). Low magnifications thin it out. A bar is drawn only {@link #R2_MIN_DOT_GAP_PX} clear rows
     * below the centre dot and {@link #R2_MIN_LADDER_PITCH_PX} from every bar that outranks it in
     * {@link #R2_LADDER_PRIORITY}, shown or not, so zooming in only ever adds bars. A label is drawn only where its
     * outlined digits keep off the wire's hashes and posts, the ranging brackets and every other label (the
     * {@link Builder#keepOut keep-out} boxes): it tries its own side, then the other, then up to
     * {@link #R2_LABEL_MAX_LOWER_PX} px lower while it stays nearer its own bar than the next one, else the bar stays
     * bare. With room for everything that is the full layout: all five bars, each label centred on its bar on its
     * own side.
     * AP 抬枪梯（发光）：每个距离在其抬枪角处一条横杠，半宽 {@link #ladderHalfWidthMrad}（至少 3 像素），标签在固定列中
     * 按距离右、左、右……交替。落在中心镂空内的抬枪无需横杠（精确枪手下的 100 格）。低倍率下会抽稀：横杠须与中心点相隔
     * {@link #R2_MIN_DOT_GAP_PX} 个空行，并与 {@link #R2_LADDER_PRIORITY} 中优先级更高的每条横杠（无论是否显示）相距
     * {@link #R2_MIN_LADDER_PITCH_PX}，因此放大时横杠只增不减。标签只在带描边的数字避开十字线的刻度与粗柱、测距括号和其他
     * 所有标签（{@link Builder#keepOut 避让框}）时绘制：先试本侧，再试另一侧，再最多下移 {@link #R2_LABEL_MAX_LOWER_PX}
     * 像素（仍须离自己的横杠比离下一条更近），否则横杠不带标签。空间足够时就是完整布局：五条横杠，标签各在本侧并与横杠居中。
     *
     * @param dotBottom first row below the illuminated centre dot / 发光中心点下方的第一行
     */
    private static void addLadder(Builder out, double ppt, double marksman, int dotBottom) {
        int s = out.scale;
        int count = AP_HOLDOVER_DISTANCES.size();
        double[] holdMrad = new double[count];
        boolean[] drawn = new boolean[count];
        for (int index = 0; index < count; index++) {
            holdMrad[index] = apHoldoverMrad(AP_HOLDOVER_DISTANCES.get(index), marksman);
        }
        for (int rank = 0; rank < R2_LADDER_PRIORITY.size(); rank++) {
            int index = AP_HOLDOVER_DISTANCES.indexOf(R2_LADDER_PRIORITY.get(rank));
            int y = px(ppt, holdMrad[index]);
            // On the trunk (by angle, so two rounded pixels never decide it) and inside the lens.
            // 位于主干上（按角度判断，不由两个取整像素决定）且在镜片内。
            boolean clear = holdMrad[index] >= R2_OPEN_CENTRE_MRAD && holdMrad[index] < R2_TRUNK_END_MRAD
                    && y < out.radius && y >= dotBottom + R2_MIN_DOT_GAP_PX * s;
            for (int above = 0; clear && above < rank; above++) {
                double other = holdMrad[AP_HOLDOVER_DISTANCES.indexOf(R2_LADDER_PRIORITY.get(above))];
                clear = other < R2_OPEN_CENTRE_MRAD || Math.abs(mradToPixels(holdMrad[index], ppt)
                        - mradToPixels(other, ppt)) >= R2_MIN_LADDER_PITCH_PX * s;
            }
            drawn[index] = clear;
        }
        int[] halves = new int[count];
        for (int index = 0; index < count; index++) {
            if (drawn[index]) {
                int y = px(ppt, holdMrad[index]);
                halves[index] = Math.max(3 * s, px(ppt, ladderHalfWidthMrad(AP_HOLDOVER_DISTANCES.get(index))));
                out.illum(-halves[index], y, halves[index] + 1, y + s, ILLUM_COLOR);
            }
        }
        // Every label gets its centred place before any is lowered, in priority order, so a lowered label never
        // takes the row a centred one needs. / 所有标签先按优先级放在居中位置，之后才下移，下移的标签不会占用居中标签的行。
        Label[] labels = new Label[count];
        for (int lower = 0; lower <= R2_LABEL_MAX_LOWER_PX; lower++) {
            for (int distance : R2_LADDER_PRIORITY) {
                int index = AP_HOLDOVER_DISTANCES.indexOf(distance);
                if (!drawn[index] || labels[index] != null) {
                    continue;
                }
                double bar = exact(ppt, holdMrad[index]);
                int below = index + 1;
                while (below < count && !drawn[below]) {
                    below++;
                }
                if (below < count && 2.0 * lower * s >= exact(ppt, holdMrad[below]) - bar) {
                    continue;
                }
                String text = Integer.toString(distance);
                int column = Math.max(halves[index], px(ppt, R2_LABEL_COLUMN_MRAD)) + 4 * s;
                double x = Math.max(Math.max(3 * s, exact(ppt, ladderHalfWidthMrad(distance))),
                        exact(ppt, R2_LABEL_COLUMN_MRAD)) + 4 * s;
                double width = approximateDigitWidth(text, s);
                double top = bar + (lower - 3) * s;
                boolean ownRight = index % 2 == 0;
                for (boolean right : new boolean[]{ownRight, !ownRight}) {
                    double x0 = right ? x : -x - width;
                    if (out.clearOfKeepOuts(x0 - s, top - s, x0 + width + s, top + (DIGIT_HEIGHT + 1) * s)) {
                        out.keepOut(x0, top, x0 + width, top + DIGIT_HEIGHT * s);
                        labels[index] = new Label(text, right ? column : -column, px(ppt, holdMrad[index])
                                + (lower - 3) * s, s, Layer.ILLUM, right ? Anchor.LEFT : Anchor.RIGHT);
                        out.labels.add(labels[index]);
                        break;
                    }
                }
            }
        }
        for (int index = 0; index < count; index++) {
            if (drawn[index]) {
                Label label = labels[index];
                out.holdovers.add(new HoldoverMark(AP_HOLDOVER_DISTANCES.get(index), holdMrad[index],
                        px(ppt, holdMrad[index]), label != null,
                        label != null ? label.anchor() == Anchor.LEFT : index % 2 == 0));
            }
        }
    }

    /**
     * PSO-style person-height brackets: feet on a baseline 30 mrad below centre, one upright per distance whose height
     * is a standing player at that range, a top serif, the range underneath and a 4 x 8 legend glyph at the right end.
     * Pieces that would leave the lens are skipped, and the whole block is left out while its columns are closer than
     * {@link #R2_MIN_RANGING_PITCH_PX} (low magnifications, where the brackets bunch up under the ladder).
     * PSO 式人形高度括号：脚部位于中心下方 30 密位的基线上，每个距离一根竖线，高度为该距离下的站立玩家，顶部有短横，
     * 下方标注距离，右端有 4 x 8 图例小人。会超出镜片的部分被跳过；列间距不足 {@link #R2_MIN_RANGING_PITCH_PX} 时
     * （低倍率，括号会挤在抬枪梯下方）整块不画。
     */
    private static void addRanging(Builder out, double ppt) {
        int s = out.scale;
        double pitchMrad = Double.MAX_VALUE;
        for (int index = 1; index < R2_RANGING_X_MRAD.size(); index++) {
            pitchMrad = Math.min(pitchMrad, Math.abs(R2_RANGING_X_MRAD.get(index) - R2_RANGING_X_MRAD.get(index - 1)));
        }
        if (mradToPixels(pitchMrad, ppt) < R2_MIN_RANGING_PITCH_PX * s) {
            return;
        }
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
        // Ladder labels keep off the baseline and everything under it, and off each bracket from its serif down.
        // 抬枪标签避开基线及其下方的一切，并避开每个括号自顶部短横以下的部分。
        out.keepOut(Double.NEGATIVE_INFINITY, exact(ppt, R2_RANGING_BASE_MRAD), Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY);
        int serif = Math.max(2 * s, px(ppt, R2_SERIF_HALF_MRAD));
        int lastLabelLeft = Integer.MAX_VALUE;
        for (int index = 0; index < AP_HOLDOVER_DISTANCES.size(); index++) {
            int distance = AP_HOLDOVER_DISTANCES.get(index);
            int x = -px(ppt, R2_RANGING_X_MRAD.get(index));
            int top = px(ppt, R2_RANGING_BASE_MRAD - personHeightMrad(distance));
            out.etch(x, top, x + s, base);
            out.etch(x - serif, top, x + serif + 1, top + s);
            double column = -exact(ppt, R2_RANGING_X_MRAD.get(index));
            out.keepOut(column - serif, exact(ppt, R2_RANGING_BASE_MRAD - personHeightMrad(distance)),
                    column + serif + 1, Double.POSITIVE_INFINITY);
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
     * R1: second focal plane, so the mil spacing is the 6x one at every magnification (one mil-dot covers 1.51 mrad at
     * 4x). No labels and no ladder: AP holds are read off the dots at 6x (2.5 / 4 / 7.5 / 10 / 13.75 dots).
     * R1：第二焦平面，因此在任何倍率下密位间距都按 6 倍计算（4 倍时一个密位点覆盖 1.51 密位）。无标签、无抬枪梯：AP 抬枪
     * 在 6 倍下按点读取（2.5 / 4 / 7.5 / 10 / 13.75 点）。
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
        final List<double[]> keepOuts = new ArrayList<>();
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

        /**
         * Reserves an area ladder labels must keep off, in unrounded pixels ({@link #exact}), [x0, x1) x [y0, y1).
         * 以未取整像素（{@link #exact}）登记一块抬枪标签必须避开的区域。
         */
        void keepOut(double x0, double y0, double x1, double y1) {
            keepOuts.add(new double[]{x0, y0, x1, y1});
        }

        /** True when the area touches no keep-out. / 该区域不与任何避让框相交时为 true。 */
        boolean clearOfKeepOuts(double x0, double y0, double x1, double y1) {
            for (double[] box : keepOuts) {
                if (box[0] < x1 && x0 < box[2] && box[1] < y1 && y0 < box[3]) {
                    return false;
                }
            }
            return true;
        }

        Layout build() {
            return new Layout(List.copyOf(rects), List.copyOf(labels), List.copyOf(holdovers));
        }
    }
}
