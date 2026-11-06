package dev.caecorthus.sparkwitch.client.bear.launcher;

import dev.caecorthus.sparkwitch.roles.civilian.bear.launcher.M32Ballistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Pure layout of the lit-red M2A1 reflex reticle ({@link M32ReticleOverlay}) from the BEAR art package's vector spec
 * (README §7), in framebuffer pixels relative to the exact screen centre (the bore line; +y down). Both designs are
 * built ({@link M32ReticleStyle}); {@link #STYLE} picks the one that ships, so the owner's choice is a one-line change.
 * <ul>
 *   <li>Scale: spec pixels are 1080p framebuffer px multiplied by {@code s = max(1, round(H / 1080))} (stroke
 *   thickness included); angular sizes are {@code ang_px(a) = ppt * tan(a / 1000)} with
 *   {@code ppt = (H / 2) / tan(vfov / 2)} from the frame's real projection FOV, so they need no {@code s}.</li>
 *   <li>Rows: {@code y(D) = ang_px(holdover(D))}, the holdover in mrad from {@link M32Ballistics#holdoverRadians},
 *   re-solved every frame at the live interpolated pitch. A NaN row (the round never gets that far before the backup
 *   fuse) or one too close to the screen edge ({@link #onScale}) is skipped. Our drag is 0.998, not the artist's
 *   assumed 0.995, so the README's pixel table is only approximate.</li>
 *   <li>Columns: the centre stroke is the pixel block {@code [0, s)}; every left element mirrors its right twin about
 *   {@code s / 2}, so the reticle is exactly symmetric at every scale.</li>
 *   <li>Paint: lit cores {@link #LIT_COLOR}, dim cores {@link #LIT_DIM_COLOR}, and a key ring {@link #KEY_COLOR} of
 *   every core grown by {@code s} (a 3x3 dilation at 1080p). The overlay draws lit, then dim, then key quads, each a
 *   little farther in depth, so a pixel is painted once by its top layer (lit over dim over key) and the key never
 *   sits under a core, exactly the spec's "dilation minus the mask". The optional glow is not drawn.</li>
 *   <li>Out of range: when the dialled row is skipped, its aim element (option 1's lit row, option 2's dot and ring)
 *   is hidden, a grey {@link #OUT_OF_RANGE_TEXT} sits just below the bottom of the scale, and the drum's current value
 *   turns grey. No mark ever shows a place the round does not reach.</li>
 *   <li>Range-dial drum (both options): there is no lens window, so its right edge is placed at
 *   {@code cx - ang_px(60) - 46 px} (owner-facing WP1 choice, outside option 1's grid and option 2's ring), vertically
 *   centred on the bore line.</li>
 * </ul>
 * 依据 BEAR 美术包矢量规格（README 第 7 节）对发光红色 M2A1 反射式分划（{@link M32ReticleOverlay}）的纯布局，单位为帧缓冲像素，
 * 相对于屏幕精确中心（即枪膛线；+y 向下）。两种设计都已实现（{@link M32ReticleStyle}），由 {@link #STYLE} 选择实际使用的一种，
 * 因此所有者的选择只需改一行。尺度：规格像素为 1080p 帧缓冲像素乘以 {@code s = max(1, round(H / 1080))}（含线宽）；角度尺寸为
 * {@code ang_px(a) = ppt * tan(a / 1000)}，其中 {@code ppt = (H / 2) / tan(vfov / 2)} 取自本帧真实投影视场角，因此无需乘
 * {@code s}。刻度行：{@code y(D) = ang_px(holdover(D))}，抬高量（毫弧度）来自 {@link M32Ballistics#holdoverRadians}，每帧按实时
 * 插值俯仰角重新求解；NaN 行（榴弹在备用引信前飞不到那么远）或离屏幕边缘过近的行（{@link #onScale}）跳过。我们的阻力为
 * 0.998，而非美术假设的 0.995，因此 README 的像素表只是近似。列：中心线条为像素块 {@code [0, s)}；左侧元素都与右侧对应元素
 * 关于 {@code s / 2} 镜像，因此任何尺度下分划都严格对称。配色：发光主体 {@link #LIT_COLOR}、暗主体 {@link #LIT_DIM_COLOR}，以及
 * 由每个主体扩展 {@code s} 得到的描边 {@link #KEY_COLOR}（1080p 下即 3x3 膨胀）。叠加层依次绘制发光、暗、描边四边形，每个都略远
 * 一点，因此每个像素只由其最上层绘制一次（发光 > 暗 > 描边），描边从不位于主体之下，正是规格中的“膨胀减去蒙版”。不绘制可选的
 * 辉光。超出射程：当前刻度行被跳过时，隐藏其瞄准元素（方案 1 的发光行、方案 2 的红点与圆环），在刻度尺底部下方显示灰色
 * {@link #OUT_OF_RANGE_TEXT}，刻度鼓当前值变灰；标记从不指向榴弹到不了的位置。射程刻度鼓（两种方案）：没有镜片窗口，因此其
 * 右缘放在 {@code cx - ang_px(60) - 46 像素}（WP1 的取舍，位于方案 1 网格与方案 2 圆环之外），竖直方向以枪膛线为中心。
 */
public final class M32ReticleRules {
    /** The shipped design; flip this one line to switch. / 实际使用的设计；改这一行即可切换。 */
    public static final M32ReticleStyle STYLE = M32ReticleStyle.RED_DOT_LADDER;

    public static final int LIT_COLOR = 0xFFFF3B2F;
    public static final int LIT_DIM_COLOR = 0x96FF3B2F;
    public static final int KEY_COLOR = 0x8C200404;
    /**
     * Label key copies are opaque, like the PGO-7 digits: the text layer re-sorts its quads, so translucent copies
     * would stack. / 标签描边副本不透明（与 PGO-7 数字相同）：文字渲染层会重排四边形，半透明副本会叠加。
     */
    public static final int LABEL_KEY_COLOR = 0xFF200404;
    public static final int OUT_OF_RANGE_COLOR = 0xFF8C8C8C;
    public static final String OUT_OF_RANGE_TEXT = "--";
    /** Labels: the vanilla font at 2 px per texel (x s); digits are 7 texels tall. / 标签：原版字体每纹素 2 像素。 */
    public static final int TEXT_TEXEL_PX = 2;
    public static final int DIGIT_TEXELS = 7;

    public static final int DRUM_FILL_COLOR = 0xDC0E0F11;
    public static final int DRUM_OUTLINE_COLOR = 0xFF464A50;
    public static final int DRUM_KNURL_COLOR = 0xFF5A5F66;
    public static final int DRUM_NEIGHBOUR_COLOR = 0x6EC8C8C8;
    public static final int DRUM_UNIT_COLOR = 0xFFC8C8C8;
    public static final String DRUM_UNIT_TEXT = "m";

    // ---- shared / 共用 ----
    /** Kept clear of the screen edge; a row past it is skipped. / 与屏幕边缘的留白；超出的行跳过。 */
    static final int EDGE_MARGIN = 12;
    /** Labelled ranges: 50 / 100 / 150 / 200 m. / 带标签的射程。 */
    static final int LABELLED_EVERY_METRES = 50;
    static final int OUT_OF_RANGE_DROP = 3;

    // ---- option 1: quadrant grid / 方案 1：象限网格 ----
    static final int CENTER_GAP = 6;
    static final double ARM_MRAD = 60.0;
    static final double UPPER_ARM_MRAD = 40.0;
    static final double[] LEAD_COLUMNS_MRAD = {20.0, 40.0};
    static final int BRACKET = 4;
    static final int LADDER_PAST = 4;
    static final double LONG_ROW_MRAD = 10.0;
    static final double SHORT_ROW_MRAD = 5.0;
    static final double SELECTED_ROW_MRAD = 16.0;
    /** Chevron columns start this far past the selected row's end (spec: half + 3 px). / 箭头列距选中行末端的间隙。 */
    static final int CHEVRON_GAP = 2;
    static final int CHEVRON_STEPS = 3;
    static final int LABEL_GAP = 11;
    static final int SELECTED_LABEL_GAP = 17;

    // ---- option 2: red dot + holdover ladder / 方案 2：红点与抬高量刻度尺 ----
    static final int DOT_RADIUS = 3;
    static final double RING_MRAD = 26.0;
    /** Half of each 28-degree ring gap at 0/90/180/270 degrees. / 0/90/180/270 度处 28 度缺口的一半。 */
    static final double RING_GAP_HALF_DEGREES = 14.0;
    static final int BORE_ARM = 3;
    static final int LADDER_OFFSET = 6;
    static final int LADDER_TOP = 6;
    static final int TICK = 3;
    static final int LONG_TICK = 6;
    /** Label left edge past the tick's exclusive end (spec: tick end + 4 px, inclusive). / 标签左缘与刻线末端的间隙。 */
    static final int TICK_LABEL_GAP = 3;

    // ---- range-dial drum / 射程刻度鼓 ----
    static final int DRUM_GAP = 46;
    static final int DRUM_WIDTH = 44;
    static final int DRUM_HEIGHT = 92;
    static final int DRUM_RADIUS = 6;
    static final int DRUM_KNURLS = 9;
    static final int DRUM_KNURL_TOP = 6;
    static final int DRUM_KNURL_PITCH = 10;
    static final int DRUM_KNURL_X0 = 2;
    static final int DRUM_KNURL_X1 = 7;
    static final int DRUM_ROW_PITCH = 26;
    static final int DRUM_TEXT_SHIFT = 3;
    static final int DRUM_POINTER_TIP = 2;
    static final int DRUM_POINTER_BASE = 8;
    static final int DRUM_POINTER_HALF = 5;
    static final int DRUM_UNIT_GAP = 4;

    private static final int NO_ROW = Integer.MIN_VALUE;
    private static final double RING_GAP_TAN = Math.tan(Math.toRadians(RING_GAP_HALF_DEGREES));

    private M32ReticleRules() {
    }

    /** Framebuffer px per spec px: round(H / 1080), at least 1. / 每个规格像素对应的帧缓冲像素。 */
    public static int strokeScale(double framebufferHeight) {
        if (!(framebufferHeight > 0.0) || !Double.isFinite(framebufferHeight)) {
            return 1;
        }
        return (int) Math.max(1L, Math.round(framebufferHeight / 1080.0));
    }

    /** {@code ppt = (H / 2) / tan(vfov / 2)}; NaN for an unusable FOV or height. / 每单位正切的像素数。 */
    public static double pixelsPerTangent(double projectionFovDegrees, double framebufferHeight) {
        if (!(projectionFovDegrees > 0.0) || !(projectionFovDegrees < 180.0) || !(framebufferHeight > 0.0)
                || !Double.isFinite(framebufferHeight)) {
            return Double.NaN;
        }
        return framebufferHeight / 2.0 / Math.tan(Math.toRadians(projectionFovDegrees) / 2.0);
    }

    /** {@code ang_px(a) = ppt * tan(a / 1000)}. / 角度（毫弧度）对应的像素。 */
    public static double angPx(double mrad, double pixelsPerTangent) {
        return pixelsPerTangent * Math.tan(mrad / 1000.0);
    }

    /**
     * Framebuffer px below the screen centre for a holdover angle; NaN for a non-finite or right-angle holdover, an
     * unusable FOV or height.
     * 抬高角对应的屏幕中心下方帧缓冲像素；抬高量非有限或达到直角、视场角或高度不可用时为 NaN。
     */
    public static double aimOffsetPx(double holdoverRadians, double projectionFovDegrees, double framebufferHeight) {
        double ppt = pixelsPerTangent(projectionFovDegrees, framebufferHeight);
        if (!Double.isFinite(holdoverRadians) || !(Math.abs(holdoverRadians) < Math.PI / 2.0) || !Double.isFinite(ppt)) {
            return Double.NaN;
        }
        return angPx(holdoverRadians * 1000.0, ppt);
    }

    /** {@code y(D)} at this pitch, before rounding (NaN when out of range). / 此俯仰角下的 {@code y(D)}（取整前）。 */
    public static double aimOffsetFor(float pitchDegrees, int metres, double projectionFovDegrees,
                                      double framebufferHeight) {
        return aimOffsetPx(M32Ballistics.holdoverRadians(pitchDegrees, metres), projectionFovDegrees,
                framebufferHeight);
    }

    /** A finite offset that stays {@link #EDGE_MARGIN} clear of the screen edge. / 有限且离屏幕边缘足够远的偏移。 */
    public static boolean onScale(double offsetPx, double framebufferHeight, int strokeScale) {
        return Double.isFinite(offsetPx)
                && Math.abs(offsetPx) <= framebufferHeight / 2.0 - EDGE_MARGIN * (double) strokeScale;
    }

    /** The top pixel row of a row at {@code offsetPx}. / 偏移处刻度行的顶像素行。 */
    public static int rowTop(double offsetPx) {
        return (int) Math.floor(offsetPx);
    }

    public static boolean isLabelled(int metres) {
        return metres % LABELLED_EVERY_METRES == 0;
    }

    /** D / 10 as on the PGO-7: 5, 10, 2.5, 12.5. / 与 PGO-7 相同的 D / 10 标签。 */
    public static String rangeLabel(int metres) {
        int tenths = Math.max(0, metres);
        return tenths % 10 == 0 ? Integer.toString(tenths / 10) : (tenths / 10) + "." + (tenths % 10);
    }

    /**
     * The reticle for one frame. {@code inkWidthTexels} measures a text's inked width in font texels (the vanilla
     * width minus its trailing gap).
     * 一帧的分划。{@code inkWidthTexels} 给出文字着墨宽度（字体纹素，即原版宽度减去末尾间隙）。
     */
    public static Reticle layout(M32ReticleStyle style, float pitchDegrees, double projectionFovDegrees,
                                 double framebufferHeight, int dialMetres, ToIntFunction<String> inkWidthTexels) {
        int s = strokeScale(framebufferHeight);
        int dialled = M32Ballistics.clampDial(dialMetres);
        double ppt = pixelsPerTangent(projectionFovDegrees, framebufferHeight);
        Builder b = new Builder(s, inkWidthTexels);
        int[] settings = M32Ballistics.dialSettings();
        int[] rows = new int[settings.length];
        int lowest = NO_ROW;
        int dialIndex = 0;
        for (int i = 0; i < settings.length; i++) {
            if (settings[i] == dialled) {
                dialIndex = i;
            }
            double offset = aimOffsetFor(pitchDegrees, settings[i], projectionFovDegrees, framebufferHeight);
            rows[i] = onScale(offset, framebufferHeight, s) ? rowTop(offset) : NO_ROW;
            if (rows[i] != NO_ROW) {
                lowest = Math.max(lowest, rows[i]);
            }
        }
        boolean inRange = rows[dialIndex] != NO_ROW;
        if (Double.isFinite(ppt)) {
            if (style == M32ReticleStyle.QUADRANT_GRID) {
                quadrantGrid(b, ppt, settings, rows, dialled, lowest, inRange);
            } else {
                redDotLadder(b, ppt, settings, rows, dialled, lowest, inRange);
            }
        }
        List<Rect> key = new ArrayList<>(b.lit.size() + b.dim.size());
        for (List<Rect> cores : List.of(b.lit, b.dim)) {
            for (Rect r : cores) {
                key.add(new Rect(r.x0() - s, r.y0() - s, r.x1() + s, r.y1() + s));
            }
        }
        Drum drum = Double.isFinite(ppt) ? drum(b, ppt, settings, dialIndex, inRange) : null;
        return new Reticle(style, s, b.textScale, List.copyOf(b.lit), List.copyOf(b.dim), List.copyOf(key),
                List.copyOf(b.labels), inRange, dialled, inRange ? rows[dialIndex] : NO_ROW, drum);
    }

    /** Option 1 (README §7, steps 1-8). / 方案 1。 */
    private static void quadrantGrid(Builder b, double ppt, int[] settings, int[] rows, int dialled, int lowest,
                                     boolean inRange) {
        int s = b.s;
        int gap = CENTER_GAP * s;
        // 1-2: lit horizontal arms and upper arm, open centre. / 发光的横臂与上臂，中心留空。
        int armEnd = floor(angPx(ARM_MRAD, ppt)) + 1;
        b.mirrored(b.lit, gap, armEnd, 0, s);
        b.rect(b.lit, 0, s - (floor(angPx(UPPER_ARM_MRAD, ppt)) + 1), s, s - gap);
        // 3: dim lower ladder to y(200) + 4 px (the lowest row shown). / 暗下臂刻度尺。
        int ladderEnd = lowest == NO_ROW ? NO_ROW : lowest + (LADDER_PAST + 1) * s;
        if (ladderEnd != NO_ROW) {
            b.rect(b.dim, 0, gap, s, ladderEnd);
        }
        for (double column : LEAD_COLUMNS_MRAD) {
            int x = floor(angPx(column, ppt));
            // 5: upper-quadrant L brackets, inward and down from (x, -ang(column)). / 上象限 L 形角标。
            int y = floor(-angPx(column, ppt));
            b.mirrored(b.dim, x - BRACKET * s, x + s, y, y + s);
            b.mirrored(b.dim, x, x + s, y + s, y + (BRACKET + 1) * s);
            // 4: lead dots on every range row. / 每个射程行上的提前量点。
            for (int row : rows) {
                if (row != NO_ROW) {
                    b.mirrored(b.dim, x, x + s, row, row + s);
                }
            }
        }
        for (int i = 0; i < settings.length; i++) {
            int y = rows[i];
            if (y == NO_ROW) {
                continue;
            }
            if (settings[i] == dialled) {
                // 7: the lit selected row, 2 px, with 3-step inward chevrons; 8: its label 17 px left.
                // 发光的选中行，2 像素厚，两端三级向内箭头；其标签在左侧 17 像素处。
                int half = floor(angPx(SELECTED_ROW_MRAD, ppt));
                b.rect(b.lit, -half, y, s + half, y + 2 * s);
                for (int k = 0; k < CHEVRON_STEPS; k++) {
                    int column = s + half + CHEVRON_GAP * s + k * s;
                    b.mirrored(b.lit, column, column + s, y - k * s, y + 2 * s + k * s);
                }
                b.labelRightAligned(rangeLabel(settings[i]), -half - SELECTED_LABEL_GAP * s, y, 2 * s, LIT_COLOR);
            } else {
                // 6: dim rows, long for the labelled ranges; 8: labels 11 px left. / 暗刻度行；标签在左侧 11 像素处。
                boolean labelled = isLabelled(settings[i]);
                int half = floor(angPx(labelled ? LONG_ROW_MRAD : SHORT_ROW_MRAD, ppt));
                b.rect(b.dim, -half, y, s + half, y + s);
                if (labelled) {
                    b.labelRightAligned(rangeLabel(settings[i]), -half - LABEL_GAP * s, y, s, LIT_DIM_COLOR);
                }
            }
        }
        if (!inRange) {
            b.labelRightAligned(OUT_OF_RANGE_TEXT, -LABEL_GAP * s, outOfRangeTop(b, lowest),
                    DIGIT_TEXELS * b.textScale, OUT_OF_RANGE_COLOR);
        }
    }

    /** Option 2 (README §7, steps 1-6). / 方案 2。 */
    private static void redDotLadder(Builder b, double ppt, int[] settings, int[] rows, int dialled, int lowest,
                                     boolean inRange) {
        int s = b.s;
        double ringRadius = angPx(RING_MRAD, ppt);
        int ladderX = floor(ringRadius) + LADDER_OFFSET * s;
        // 3: dim bore "+" (horizontal stroke whole, vertical halves, so nothing overlaps). / 暗枪膛十字。
        b.rect(b.dim, -BORE_ARM * s, 0, s + BORE_ARM * s, s);
        b.rect(b.dim, 0, -BORE_ARM * s, s, 0);
        b.rect(b.dim, 0, s, s, s + BORE_ARM * s);
        // 4: dim ladder from cy + 6 px down to y(200) (the lowest row shown). / 暗刻度尺。
        int ladderEnd = lowest == NO_ROW ? NO_ROW : lowest + s;
        if (ladderEnd != NO_ROW) {
            b.rect(b.dim, ladderX, LADDER_TOP * s, ladderX + s, ladderEnd);
        }
        // 5-6: ticks right of it at every row but the dialled one, labels after the long ones.
        // 除当前刻度外每行右侧一条刻线，长刻线后接标签。
        for (int i = 0; i < settings.length; i++) {
            int y = rows[i];
            if (y == NO_ROW || settings[i] == dialled) {
                continue;
            }
            boolean labelled = isLabelled(settings[i]);
            int end = ladderX + s + (labelled ? LONG_TICK : TICK) * s;
            b.rect(b.dim, ladderX + s, y, end, y + s);
            if (labelled) {
                b.label(rangeLabel(settings[i]), end + TICK_LABEL_GAP * s, y, s, LIT_DIM_COLOR);
            }
        }
        int dialIndex = indexOf(settings, dialled);
        if (inRange) {
            // 1-2: the lit dot and the broken ring at (cx, y(dial)). / 位于 (cx, y(刻度)) 的发光红点与断开的圆环。
            int y = rows[dialIndex];
            disc(b.lit, s, DOT_RADIUS * s, y);
            ring(b.lit, s, ringRadius, y);
        } else {
            b.label(OUT_OF_RANGE_TEXT, ladderX + s + LONG_TICK * s + TICK_LABEL_GAP * s, outOfRangeTop(b, lowest),
                    DIGIT_TEXELS * b.textScale, OUT_OF_RANGE_COLOR);
        }
    }

    /**
     * The range-dial drum, its right edge at the left arm's start (mirror of {@code ang_px(60)}) minus 46 px.
     * 射程刻度鼓，右缘位于左臂起点（{@code ang_px(60)} 的镜像）再向左 46 像素。
     */
    private static Drum drum(Builder b, double ppt, int[] settings, int dialIndex, boolean inRange) {
        int s = b.s;
        int right = s - (floor(angPx(ARM_MRAD, ppt)) + 1) - DRUM_GAP * s;
        int width = DRUM_WIDTH * s;
        int height = DRUM_HEIGHT * s;
        int left = right - width;
        int top = -height / 2;
        List<Rect> outline = new ArrayList<>();
        List<Rect> fill = new ArrayList<>();
        roundedRect(outline, fill, left, top, width, height, DRUM_RADIUS * s, s);
        List<Rect> knurls = new ArrayList<>(DRUM_KNURLS);
        for (int k = 0; k < DRUM_KNURLS; k++) {
            int y = top + DRUM_KNURL_TOP * s + k * DRUM_KNURL_PITCH * s;
            knurls.add(new Rect(left + DRUM_KNURL_X0 * s, y, left + DRUM_KNURL_X1 * s, y + s));
        }
        // A left-pointing index triangle just right of the drum, its tip row on the bore line.
        // 鼓右侧向左指的指示三角，尖端所在行位于枪膛线。
        List<Rect> pointer = new ArrayList<>();
        int half = DRUM_POINTER_HALF * s;
        int span = (DRUM_POINTER_BASE - DRUM_POINTER_TIP) * s;
        for (int j = -half; j <= half; j++) {
            int x0 = right + DRUM_POINTER_TIP * s + (int) Math.round(span * (double) Math.abs(j) / half);
            pointer.add(new Rect(x0, j, right + DRUM_POINTER_BASE * s + 1, j + 1));
        }
        List<DrumText> texts = new ArrayList<>(4);
        int centre = left + width / 2;
        int textScale = b.textScale;
        int digitHalf = DIGIT_TEXELS * textScale / 2;
        for (int offset = -1; offset <= 1; offset++) {
            int k = dialIndex + offset;
            if (k < 0 || k >= settings.length) {
                continue;
            }
            String text = Integer.toString(settings[k]);
            int x = centre - b.inkWidth(text) * textScale / 2 + DRUM_TEXT_SHIFT * s;
            int y = -digitHalf + offset * DRUM_ROW_PITCH * s;
            boolean current = offset == 0;
            int color = current ? (inRange ? LIT_COLOR : OUT_OF_RANGE_COLOR) : DRUM_NEIGHBOUR_COLOR;
            texts.add(new DrumText(text, x, y, color, current));
        }
        texts.add(new DrumText(DRUM_UNIT_TEXT, centre - b.inkWidth(DRUM_UNIT_TEXT) * textScale / 2,
                top + height + DRUM_UNIT_GAP * s, DRUM_UNIT_COLOR, true));
        return new Drum(left, top, right, top + height, mergeRows(outline), List.copyOf(knurls), List.copyOf(pointer),
                mergeRows(fill), List.copyOf(texts));
    }

    /**
     * A filled disc of radius {@code radius} around the centre block {@code [0, s) x [rowTop, rowTop + s)}, one rect
     * per pixel row. / 以中心块为圆心、半径 {@code radius} 的实心圆，每像素行一个矩形。
     */
    static void disc(List<Rect> out, int s, int radius, int rowTop) {
        double cx = s / 2.0;
        double cy = rowTop + s / 2.0;
        double limit = (radius + 0.5) * (radius + 0.5);
        for (int j = (int) Math.floor(cy - radius - 1); j <= (int) Math.ceil(cy + radius + 1); j++) {
            double dy = j + 0.5 - cy;
            int x0 = Integer.MAX_VALUE;
            int x1 = Integer.MIN_VALUE;
            for (int i = (int) Math.floor(cx - radius - 1); i <= (int) Math.ceil(cx + radius + 1); i++) {
                double dx = i + 0.5 - cx;
                if (dx * dx + dy * dy <= limit) {
                    x0 = Math.min(x0, i);
                    x1 = Math.max(x1, i + 1);
                }
            }
            if (x1 > x0) {
                out.add(new Rect(x0, j, x1, j + 1));
            }
        }
    }

    /**
     * An {@code s}-thick ring of radius {@code radius} around the same centre, broken by 28-degree gaps at
     * 0/90/180/270 degrees, as horizontal pixel runs. / 同一圆心、半径 {@code radius}、厚 {@code s} 的圆环，在 0/90/180/270
     * 度处有 28 度缺口，以水平像素段表示。
     */
    static void ring(List<Rect> out, int s, double radius, int rowTop) {
        double cx = s / 2.0;
        double cy = rowTop + s / 2.0;
        double inner = Math.max(0.0, radius - s / 2.0);
        double outer = radius + s / 2.0;
        double inner2 = inner * inner;
        double outer2 = outer * outer;
        int i0 = (int) Math.floor(cx - outer - 1);
        int i1 = (int) Math.ceil(cx + outer + 1);
        for (int j = (int) Math.floor(cy - outer - 1); j <= (int) Math.ceil(cy + outer + 1); j++) {
            double dy = j + 0.5 - cy;
            int runStart = Integer.MIN_VALUE;
            for (int i = i0; i <= i1 + 1; i++) {
                boolean in = false;
                if (i <= i1) {
                    double dx = i + 0.5 - cx;
                    double d2 = dx * dx + dy * dy;
                    double ax = Math.abs(dx);
                    double ay = Math.abs(dy);
                    in = d2 >= inner2 && d2 <= outer2 && !(Math.min(ax, ay) < RING_GAP_TAN * Math.max(ax, ay));
                }
                if (in && runStart == Integer.MIN_VALUE) {
                    runStart = i;
                } else if (!in && runStart != Integer.MIN_VALUE) {
                    out.add(new Rect(runStart, j, i, j + 1));
                    runStart = Integer.MIN_VALUE;
                }
            }
        }
    }

    /**
     * A rounded rectangle as disjoint outline ({@code t} px) and fill rects. / 圆角矩形，拆成互不重叠的轮廓与填充矩形。
     */
    static void roundedRect(List<Rect> outline, List<Rect> fill, int x0, int y0, int width, int height, int radius,
                            int t) {
        int x1 = x0 + width;
        for (int v = 0; v < height; v++) {
            int y = y0 + v;
            int outer = cornerInset(v, height, radius);
            if (v < t || v >= height - t) {
                addRun(outline, x0 + outer, x1 - outer, y);
                continue;
            }
            int inner = t + cornerInset(v - t, height - 2 * t, radius - t);
            addRun(outline, x0 + outer, x0 + inner, y);
            addRun(outline, x1 - inner, x1 - outer, y);
            addRun(fill, x0 + inner, x1 - inner, y);
        }
    }

    static int cornerInset(int v, int height, int radius) {
        if (radius <= 0) {
            return 0;
        }
        double dy;
        if (v < radius) {
            dy = radius - (v + 0.5);
        } else if (v >= height - radius) {
            dy = v + 0.5 - (height - radius);
        } else {
            return 0;
        }
        return radius - (int) Math.round(Math.sqrt(Math.max(0.0, radius * (double) radius - dy * dy)));
    }

    private static void addRun(List<Rect> out, int x0, int x1, int y) {
        if (x1 > x0) {
            out.add(new Rect(x0, y, x1, y + 1));
        }
    }

    /** Joins vertically adjacent rects of the same columns. / 合并列相同且竖直相邻的矩形。 */
    static List<Rect> mergeRows(List<Rect> rows) {
        List<Rect> out = new ArrayList<>(rows.size());
        Map<Long, Integer> lastByColumns = new LinkedHashMap<>();
        for (Rect r : rows) {
            long columns = ((long) r.x0() << 32) ^ (r.x1() & 0xFFFFFFFFL);
            Integer index = lastByColumns.get(columns);
            if (index != null && out.get(index).y1() == r.y0()) {
                Rect previous = out.get(index);
                out.set(index, new Rect(previous.x0(), previous.y0(), previous.x1(), r.y1()));
            } else {
                lastByColumns.put(columns, out.size());
                out.add(r);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Top of the out-of-range text: {@link #OUT_OF_RANGE_DROP} px below the lowest row's label (rows grow with range,
     * so an out-of-range dial lies past every row shown), or just below the centre when no row is shown.
     * 超出射程文字的顶边：位于最低刻度行标签下方 {@link #OUT_OF_RANGE_DROP} 像素（刻度行随射程下移，超出射程的刻度必在所有
     * 已显示行之后）；没有任何行时紧贴中心下方。
     */
    private static int outOfRangeTop(Builder b, int lowest) {
        int s = b.s;
        if (lowest == NO_ROW) {
            return CENTER_GAP * s;
        }
        return lowest + DIGIT_TEXELS * b.textScale / 2 + OUT_OF_RANGE_DROP * s;
    }

    private static int indexOf(int[] settings, int metres) {
        for (int i = 0; i < settings.length; i++) {
            if (settings[i] == metres) {
                return i;
            }
        }
        return 0;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    /** Accumulates one layout. / 累积一次布局。 */
    private static final class Builder {
        final int s;
        final int textScale;
        final ToIntFunction<String> inkWidthTexels;
        final List<Rect> lit = new ArrayList<>();
        final List<Rect> dim = new ArrayList<>();
        final List<Label> labels = new ArrayList<>();

        Builder(int s, ToIntFunction<String> inkWidthTexels) {
            this.s = s;
            this.textScale = TEXT_TEXEL_PX * s;
            this.inkWidthTexels = inkWidthTexels == null ? text -> 0 : inkWidthTexels;
        }

        void rect(List<Rect> out, int x0, int y0, int x1, int y1) {
            if (x1 > x0 && y1 > y0) {
                out.add(new Rect(x0, y0, x1, y1));
            }
        }

        /** A right-side rect and its twin mirrored about {@code s / 2}. / 右侧矩形及其关于 {@code s / 2} 的镜像。 */
        void mirrored(List<Rect> out, int x0, int x1, int y0, int y1) {
            rect(out, x0, y0, x1, y1);
            rect(out, s - x1, y0, s - x0, y1);
        }

        int inkWidth(String text) {
            return Math.max(0, inkWidthTexels.applyAsInt(text));
        }

        /** A label vertically centred on a {@code thickness}-px row at {@code rowTop}. / 竖直居中于刻度行的标签。 */
        void label(String text, int x, int rowTop, int thickness, int color) {
            labels.add(new Label(text, x, rowTop + Math.floorDiv(thickness - DIGIT_TEXELS * textScale, 2), color));
        }

        void labelRightAligned(String text, int right, int rowTop, int thickness, int color) {
            label(text, right - inkWidth(text) * textScale, rowTop, thickness, color);
        }
    }

    /** Axis-aligned framebuffer-px rectangle [x0, x1) x [y0, y1). / 轴对齐的帧缓冲像素矩形。 */
    public record Rect(int x0, int y0, int x1, int y1) {
    }

    /** A reticle label, top-left at ({@code x}, {@code y}), drawn at the reticle's text scale. / 分划标签。 */
    public record Label(String text, int x, int y, int color) {
    }

    /** One drum text, top-left at ({@code x}, {@code y}), at the reticle's text scale. / 刻度鼓文字。 */
    public record DrumText(String text, int x, int y, int color, boolean shadow) {
    }

    /**
     * The range-dial drum: its box ({@code left..right} x {@code top..bottom}), the opaque outline, knurls and lit
     * pointer, the translucent fill (disjoint from the outline), and the previous / current / next values plus "m".
     * 射程刻度鼓：外框、不透明的轮廓、滚花与发光指针、半透明填充（与轮廓不重叠），以及上一档 / 当前 / 下一档数值与 "m"。
     */
    public record Drum(int left, int top, int right, int bottom, List<Rect> outline, List<Rect> knurls,
                       List<Rect> pointer, List<Rect> fill, List<DrumText> texts) {
    }

    /**
     * One frame's reticle: the style, stroke and text scales, lit and dim cores, their key ring, labels, whether the
     * dialled row is in range (and its top pixel row, {@code Integer.MIN_VALUE} when not), the dialled setting and the
     * drum (null for an unusable FOV or height).
     * 一帧的分划：样式、线宽与文字倍数、发光与暗主体、其描边、标签、当前刻度行是否在射程内（及其顶像素行，不在时为
     * {@code Integer.MIN_VALUE}）、当前刻度与刻度鼓（视场角或高度不可用时为 null）。
     */
    public record Reticle(M32ReticleStyle style, int strokeScale, int textScale, List<Rect> lit, List<Rect> dim,
                          List<Rect> key, List<Label> labels, boolean inRange, int dialMetres, int aimRowTop,
                          Drum drum) {
    }
}
