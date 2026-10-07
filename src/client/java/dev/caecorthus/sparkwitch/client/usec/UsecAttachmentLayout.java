package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.client.usec.UsecAttachmentModel.Card;
import dev.caecorthus.sparkwitch.client.usec.UsecAttachmentModel.PartKind;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Client only, pure geometry of the USEC attachment screen in the owner-picked U2 "field manual blueprint" layout
 * (2026-10-07, mockups {@code usec-art/ui/u2_*}): a 400 x 260 drawing sheet with the title strip, the side-view
 * drawing (rifle, dimensions, numbered callout balloons ① chamber ② magazine ③ muzzle), three detail boxes with their
 * button grids, the parts list ("BOM" table) with its notes and title block, and the bolt footer. Every coordinate is
 * panel-local; {@link #of} centres the panel and scales it down uniformly only when the scaled screen is too small.
 * {@code UsecAttachmentScreen} paints with exactly these rects and hit-tests through the same methods, so what is
 * painted is what is clicked. A restyle changes this class and {@code UsecAttachmentPaint} only. No client types:
 * the screen measures label widths and passes them in. Right and bottom edges are exclusive, like
 * {@code DrawContext.fill}.
 * 仅客户端，USEC 配件界面的纯几何，采用所有者选定的 U2「野战手册蓝图」布局（2026-10-07，样稿 {@code usec-art/ui/u2_*}）：
 * 400 × 260 的图纸，含标题栏、主视图（步枪、尺寸标注、编号引出圈 ①弹膛 ②弹匣 ③枪口）、三个带按钮格的局部详图框、零件
 * 明细表及其注释与标题栏，以及拉栓页脚。所有坐标都是面板局部坐标；{@link #of} 让面板居中，仅在缩放后屏幕过小时整体等比缩小。
 * {@code UsecAttachmentScreen} 严格按这些矩形绘制并用同样的方法判定点击，绘制即命中。换皮只需改动本类与
 * {@code UsecAttachmentPaint}。不含客户端类型，标签宽度由界面测量后传入。右缘与下缘为开区间（与 fill 一致）。
 */
public final class UsecAttachmentLayout {
    // ---- sheet ----
    public static final int PANEL_W = 400;
    public static final int PANEL_H = 260;
    public static final int SCREEN_MARGIN = 4;
    public static final int INNER = 3;
    public static final int GRID_X0 = 4;
    public static final int GRID_Y0 = 20;
    public static final int GRID_X1 = 396;
    public static final int GRID_Y1 = 256;
    public static final int GRID_STEP = 8;
    public static final int GRID_MAJOR = 40;
    public static final int ZONE_STEP = 40;
    public static final int TITLE_RULE_Y = 19;
    public static final int TITLE_ICON_X = 6;
    public static final int TITLE_ICON_Y = 6;
    public static final int TITLE_X = 18;
    public static final int TITLE_Y = 7;
    public static final int ROLE_RIGHT = 8;
    public static final int DIVIDER_X = 199;
    public static final int FOOTER_RULE_Y = 238;

    // ---- drawing (side view) ----
    public static final int VIEW_LABEL_X = 8;
    public static final int VIEW_LABEL_Y = 23;
    /** Panel position of the rifle art's local (0, 0). 步枪线稿局部 (0, 0) 在面板上的位置。 */
    public static final int RIFLE_X = 7;
    public static final int RIFLE_Y = 44;
    public static final int BORE_Y = 22;
    public static final int CHAMBER_X0 = 87;
    public static final int CHAMBER_X1 = 98;
    public static final int BARREL_END_X = 180;
    public static final int MUZZLE_X = 188;
    public static final int BUTT_TOP_X = 4;
    public static final int BUTT_TOP_Y = 11;
    public static final int KNOB_X = 53;
    public static final int KNOB_Y = 33;
    public static final int MUZZLE_ANCHOR_X = 184;
    public static final int MUZZLE_ANCHOR_Y = 19;
    /** Overall-length dimension line y (label above), and the barrel-length line. 全长与枪管长尺寸线。 */
    public static final int DIM_TOP_Y = RIFLE_Y - 11;
    public static final int DIM_BOTTOM_Y = RIFLE_Y + 56;
    public static final int BALLOON_SIZE = 11;
    /**
     * Balloon top-left and leader (from, to) per {@link Card} ordinal (① chamber, ② magazine, ③ muzzle), rifle-local.
     * 各引出圈左上角与引线起止，按 Card 序号（①弹膛、②弹匣、③枪口），步枪局部坐标。
     */
    static final int[][] BALLOONS = {{30, 39}, {105, 37}, {161, 1}};
    static final int[][] LEADERS = {{40, 41, KNOB_X - 2, KNOB_Y + 1}, {105, 41, 102, 40},
            {170, 10, MUZZLE_ANCHOR_X - 1, MUZZLE_ANCHOR_Y}};

    // ---- detail boxes (top to bottom: ① chamber, ② magazine, ③ muzzle) ----
    public static final int BOX_X = RIFLE_X;
    public static final int BOX_Y0 = 110;
    public static final int BOX_PITCH = 42;
    public static final int BOX_W = 189;
    public static final int BOX_H = 38;
    public static final int BOX_HEADER_W = 46;
    public static final int BOX_BALLOON = 4;
    public static final int BOX_LABEL_X = 18;
    public static final int BOX_LABEL_Y = 6;
    public static final int BOX_CODE_X = 5;
    public static final int BOX_CODE_Y = 22;
    public static final int BOX_WELL_X = 50;
    public static final int BOX_WELL_Y = 9;
    public static final int WELL = 20;
    public static final int BOX_TEXT_X = 75;
    public static final int BOX_LINE1_Y = 10;
    public static final int BOX_LINE2_Y = 22;
    public static final int BOX_PIPS_Y = 23;
    public static final int BOX_GRID_ROW1 = 8;
    public static final int BOX_GRID_ROW2 = 20;
    public static final int BOX_GRID_RIGHT = 4;
    /** Mockup column widths; a wider label widens its column. 样稿列宽；标签更宽时列随之加宽。 */
    public static final int GRID_COL1_MIN = 28;
    public static final int GRID_COL2_MIN = 24;
    public static final int GRID_GAP = 2;

    // ---- buttons ----
    public static final int BUTTON_H = 11;
    /** Label padding each side: "+FMJ" is 28 px wide, as in the mockup. 标签两侧内边距：「+FMJ」宽 28 像素，与样稿一致。 */
    public static final int BUTTON_PAD = 2;
    public static final int BUTTON_TEXT_Y = 2;

    // ---- parts list (BOM table) ----
    public static final int BOM_X1 = 200;
    public static final int BOM_X2 = 396;
    public static final int BOM_LABEL_Y = 23;
    public static final int TABLE_TOP = 34;
    public static final int HEADER_TEXT_Y = 37;
    public static final int HEADER_RULE_Y = 47;
    public static final int ROWS_TOP = 48;
    public static final int ROW_H = 26;
    /**
     * The rows viewport ends here (exclusive): exactly five rows, so a wheel step of one row always lands on a row edge.
     * The table's closing line sits on the viewport's last pixel row. 行视口在此结束（开区间）：恰好五行，滚轮每步一行时总落在
     * 行边界上。表格收尾线位于视口最后一行像素。
     */
    public static final int ROWS_BOTTOM = ROWS_TOP + 5 * ROW_H;
    /** Column [from, to) per {@link Column} ordinal. 各列 [起, 止)。 */
    static final int[][] COLUMNS = {{200, 217}, {218, 261}, {262, 316}, {317, 337}, {338, 396}};
    public static final int ROW_NUMBER_Y = 9;
    public static final int ROW_ICON_DX = 1;
    public static final int ROW_ICON_Y = 5;
    public static final int ROW_NAME_DX = 19;
    public static final int ROW_LINE1_Y = 4;
    public static final int ROW_LINE2_Y = 15;
    public static final int ROW_PIPS_DX = 2;
    public static final int ROW_PIPS_Y = 16;
    public static final int ROW_GRID_ROW1 = 1;
    public static final int ROW_GRID_ROW2 = 13;
    public static final int ROW_SINGLE_Y = 7;
    /** 1 px rod in the last table column, right of every button. 表格最后 1 像素列中的滚动条，位于所有按钮右侧。 */
    public static final int ROD_X = 395;
    public static final int ROD_W = 1;
    public static final int THUMB_MIN = 8;
    public static final int NOTES_X = 204;
    public static final int NOTES_Y = 185;
    public static final int NOTES_LINE = 11;

    // ---- title block ----
    public static final int BLOCK_TOP = 210;
    public static final int BLOCK_ROW = 15;
    public static final int BLOCK_LABEL_X = 202;
    public static final int BLOCK_VALUE_X = 226;
    public static final int BLOCK_SPLIT_X = 223;
    public static final int BLOCK_LABEL2_X = 315;
    public static final int BLOCK_VALUE2_X = 339;
    public static final int BLOCK_COL2_X = 312;
    public static final int BLOCK_COL3_X = 336;

    // ---- footer ----
    public static final int PILL_X = 8;
    public static final int PILL_Y = 243;
    public static final int GAUGE_W = 46;
    public static final int FOOTER_RIGHT = DIVIDER_X - 4;

    /** Parts-table columns. 明细表列。 */
    public enum Column {
        NUMBER,
        PART,
        CONTENT,
        QUANTITY,
        ACTIONS
    }

    /** Hit result: {@code group} is a card ordinal or a row index, {@code index} the button within it. 命中结果。 */
    public record Hit(int group, int index) {
    }

    /** Panel-local rect; right and bottom are exclusive. 面板局部矩形；右缘与下缘为开区间。 */
    public record Rect(int x, int y, int w, int h) {
        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    private final double originX;
    private final double originY;
    private final double scale;

    private UsecAttachmentLayout(double originX, double originY, double scale) {
        this.originX = originX;
        this.originY = originY;
        this.scale = scale;
    }

    /**
     * Centred at 1:1 when the scaled screen has room, otherwise scaled down uniformly to fit with a margin.
     * 缩放后屏幕放得下时 1:1 居中，否则整体等比缩小以留出边距。
     */
    public static UsecAttachmentLayout of(int screenWidth, int screenHeight) {
        double fit = Math.min((screenWidth - 2.0 * SCREEN_MARGIN) / PANEL_W,
                (screenHeight - 2.0 * SCREEN_MARGIN) / PANEL_H);
        double scale = Math.max(0.25, Math.min(1.0, fit));
        double width = PANEL_W * scale;
        double height = PANEL_H * scale;
        // Whole pixels at 1:1 keep every hairline crisp. / 1:1 时取整像素，细线保持清晰。
        double x = scale == 1.0 ? Math.floor((screenWidth - width) / 2) : (screenWidth - width) / 2;
        double y = scale == 1.0 ? Math.floor((screenHeight - height) / 2) : (screenHeight - height) / 2;
        return new UsecAttachmentLayout(Math.max(0, x), Math.max(0, y), scale);
    }

    public double originX() {
        return originX;
    }

    public double originY() {
        return originY;
    }

    public double scale() {
        return scale;
    }

    /** Screen point to panel-local. 屏幕坐标转面板局部坐标。 */
    public double localX(double screenX) {
        return (screenX - originX) / scale;
    }

    public double localY(double screenY) {
        return (screenY - originY) / scale;
    }

    /** Panel-local to screen, rounded outwards for scissor rects. 面板局部转屏幕坐标（剪裁矩形向外取整）。 */
    public int screenLeft(int localX) {
        return (int) Math.floor(originX + localX * scale);
    }

    public int screenRight(int localX) {
        return (int) Math.ceil(originX + localX * scale);
    }

    public int screenTop(int localY) {
        return (int) Math.floor(originY + localY * scale);
    }

    public int screenBottom(int localY) {
        return (int) Math.ceil(originY + localY * scale);
    }

    public static Rect panel() {
        return new Rect(0, 0, PANEL_W, PANEL_H);
    }

    // ---- drawing ----

    /** Panel x of a rifle-local x. 步枪局部 x 对应的面板 x。 */
    public static int rifleX(int localX) {
        return RIFLE_X + localX;
    }

    public static int rifleY(int localY) {
        return RIFLE_Y + localY;
    }

    public static Rect balloon(Card card) {
        int[] at = BALLOONS[card.ordinal()];
        return new Rect(rifleX(at[0]), rifleY(at[1]), BALLOON_SIZE, BALLOON_SIZE);
    }

    /** Leader {x0, y0, x1, y1} in panel coords; the muzzle end rises 1 px onto a suppressor. 引线起止（面板坐标）。 */
    public static int[] leader(Card card, boolean suppressed) {
        int[] l = LEADERS[card.ordinal()];
        int lift = card == Card.MUZZLE && suppressed ? 1 : 0;
        return new int[]{rifleX(l[0]), rifleY(l[1]), rifleX(l[2]), rifleY(l[3]) - lift};
    }

    // ---- detail boxes ----

    /** The balloon digit of a card (1 chamber, 2 magazine, 3 muzzle). 卡片的引出圈编号。 */
    public static int number(Card card) {
        return card.ordinal() + 1;
    }

    /** Detail boxes run ① chamber, ② magazine, ③ muzzle from the top. 详图框自上而下依次为 ①弹膛、②弹匣、③枪口。 */
    public static Rect box(Card card) {
        return new Rect(BOX_X, BOX_Y0 + card.ordinal() * BOX_PITCH, BOX_W, BOX_H);
    }

    public static Rect boxWell(Card card) {
        Rect box = box(card);
        return new Rect(box.x() + BOX_WELL_X, box.y() + BOX_WELL_Y, WELL, WELL);
    }

    public static int boxTextX(Card card) {
        return box(card).x() + BOX_TEXT_X;
    }

    /**
     * A box's buttons: the magazine box is a 2 x 2 grid (+FMJ, +AP / 退弹, 卸下); the chamber and muzzle boxes hold one
     * button in the lower right. Columns are right-aligned and never narrower than the mockup.
     * 详图框的按钮：弹匣框为 2 × 2 格（+FMJ、+AP / 退弹、卸下）；弹膛与枪口框在右下角各有一个按钮。各列右对齐，且不窄于样稿。
     */
    public static List<Rect> boxButtons(Card card, int[] widths) {
        Rect box = box(card);
        List<Rect> rects = new ArrayList<>(widths.length);
        if (card == Card.MAGAZINE) {
            int col1 = Math.max(GRID_COL1_MIN, Math.max(at(widths, 0), at(widths, 2)));
            int col2 = Math.max(GRID_COL2_MIN, Math.max(at(widths, 1), at(widths, 3)));
            int x2 = box.right() - BOX_GRID_RIGHT - col2;
            int x1 = x2 - GRID_GAP - col1;
            for (int index = 0; index < widths.length; index++) {
                boolean left = index % 2 == 0;
                int y = box.y() + (index < 2 ? BOX_GRID_ROW1 : BOX_GRID_ROW2);
                rects.add(new Rect(left ? x1 : x2, y, left ? col1 : col2, BUTTON_H));
            }
            return rects;
        }
        for (int index = 0; index < widths.length; index++) {
            int width = Math.max(GRID_COL2_MIN, widths[index]);
            rects.add(new Rect(box.right() - BOX_GRID_RIGHT - width, box.y() + BOX_GRID_ROW2, width, BUTTON_H));
        }
        return rects;
    }

    /** Width left for a box's text beside its buttons. 详图框文字在按钮左侧可用的宽度。 */
    public static int boxTextWidth(Card card, int[] widths) {
        List<Rect> buttons = boxButtons(card, widths);
        int limit = box(card).right() - BOX_GRID_RIGHT;
        for (Rect button : buttons) {
            limit = Math.min(limit, button.x());
        }
        return Math.max(0, limit - 3 - boxTextX(card));
    }

    public static @Nullable Hit hitBoxButton(double x, double y, int[][] widths) {
        for (Card card : Card.values()) {
            int[] row = card.ordinal() < widths.length ? widths[card.ordinal()] : new int[0];
            List<Rect> rects = boxButtons(card, row);
            for (int index = 0; index < rects.size(); index++) {
                if (rects.get(index).contains(x, y)) {
                    return new Hit(card.ordinal(), index);
                }
            }
        }
        return null;
    }

    public static @Nullable Card boxAt(double x, double y) {
        for (Card card : Card.values()) {
            if (box(card).contains(x, y)) {
                return card;
            }
        }
        return null;
    }

    // ---- parts table ----

    public static int columnFrom(Column column) {
        return COLUMNS[column.ordinal()][0];
    }

    public static int columnTo(Column column) {
        return COLUMNS[column.ordinal()][1];
    }

    /** The clipped rows viewport. 裁剪后的行视口。 */
    public static Rect rows() {
        return new Rect(BOM_X1, ROWS_TOP, BOM_X2 - BOM_X1, ROWS_BOTTOM - ROWS_TOP);
    }

    public static boolean scrolls(int rows) {
        return Math.max(0, rows) * ROW_H > rows().h();
    }

    public static int maxScroll(int rows) {
        return Math.max(0, Math.max(0, rows) * ROW_H - rows().h());
    }

    public static int clampScroll(int scroll, int rows) {
        return Math.max(0, Math.min(maxScroll(rows), scroll));
    }

    /** Row {@code index} at the given scroll; may lie partly outside the viewport. 第 index 行；可能部分位于视口外。 */
    public static Rect row(int index, int scroll) {
        return new Rect(BOM_X1, ROWS_TOP + index * ROW_H - scroll, BOM_X2 - BOM_X1, ROW_H);
    }

    /**
     * The bottom edge (exclusive) of the table body: the last row, or the viewport while scrolling. The closing line is
     * its last pixel row. 表体下缘（开区间）：最后一行，滚动时为视口下缘。收尾线位于其最后一行像素。
     */
    public static int tableBottom(int rows) {
        return scrolls(rows) ? ROWS_BOTTOM : ROWS_TOP + Math.max(1, rows) * ROW_H;
    }

    /**
     * A row's buttons in the 操作 column: a magazine row is a 2 x 2 grid (装入, 退弹 / +FMJ, +AP); a round or
     * suppressor row holds one centred button.
     * 行在「操作」列的按钮：弹匣行为 2 × 2 格（装入、退弹 / +FMJ、+AP）；子弹行与消音器行各有一个居中按钮。
     */
    public static List<Rect> rowButtons(PartKind kind, Rect row, int[] widths) {
        int c0 = columnFrom(Column.ACTIONS);
        int c1 = columnTo(Column.ACTIONS);
        List<Rect> rects = new ArrayList<>(widths.length);
        if (kind == PartKind.MAGAZINE) {
            int col1 = Math.max(GRID_COL1_MIN, Math.max(at(widths, 0), at(widths, 2)));
            int col2 = Math.max(GRID_COL2_MIN, Math.max(at(widths, 1), at(widths, 3)));
            int x1 = c0 + 2;
            int x2 = x1 + col1 + GRID_GAP;
            for (int index = 0; index < widths.length; index++) {
                boolean left = index % 2 == 0;
                int y = row.y() + (index < 2 ? ROW_GRID_ROW1 : ROW_GRID_ROW2);
                rects.add(new Rect(left ? x1 : x2, y, left ? col1 : col2, BUTTON_H));
            }
            return rects;
        }
        for (int width : widths) {
            int w = Math.min(c1 - c0 - 2, Math.max(GRID_COL2_MIN, width));
            rects.add(new Rect(c0 + (c1 - c0 - w) / 2, row.y() + ROW_SINGLE_Y, w, BUTTON_H));
        }
        return rects;
    }

    /** Row index under the point inside the viewport, else -1. 视口内该点下的行号，否则为 -1。 */
    public static int rowAt(double x, double y, int scroll, int rows) {
        if (!rows().contains(x, y)) {
            return -1;
        }
        for (int index = 0; index < rows; index++) {
            if (row(index, scroll).contains(x, y)) {
                return index;
            }
        }
        return -1;
    }

    /** {@code kinds[row]}, {@code widths[row][button]}; only inside the viewport. 命中行内按钮；仅限视口内。 */
    public static @Nullable Hit hitRowButton(double x, double y, int scroll, PartKind[] kinds, int[][] widths) {
        int index = rowAt(x, y, scroll, kinds.length);
        if (index < 0) {
            return null;
        }
        List<Rect> rects = rowButtons(kinds[index], row(index, scroll), widths[index]);
        for (int button = 0; button < rects.size(); button++) {
            if (rects.get(button).contains(x, y)) {
                return new Hit(index, button);
            }
        }
        return null;
    }

    /** The row icon (16 x 16) in the 零件 column. 「零件」列中的行图标（16 × 16）。 */
    public static Rect rowIcon(Rect row) {
        return new Rect(columnFrom(Column.PART) + ROW_ICON_DX, row.y() + ROW_ICON_Y, 16, 16);
    }

    /** The rod stops above the closing line. 滚动条止于收尾线之上。 */
    public static @Nullable Rect rod(int rows) {
        return scrolls(rows) ? new Rect(ROD_X, ROWS_TOP, ROD_W, ROWS_BOTTOM - 1 - ROWS_TOP) : null;
    }

    public static @Nullable Rect thumb(int scroll, int rows) {
        Rect rod = rod(rows);
        if (rod == null) {
            return null;
        }
        int content = rows * ROW_H;
        int height = Math.min(rod.h(), Math.max(THUMB_MIN, rod.h() * rod.h() / Math.max(1, content)));
        int max = maxScroll(rows);
        int y = rod.y() + (max == 0 ? 0 : clampScroll(scroll, rows) * (rod.h() - height) / max);
        return new Rect(rod.x(), y, rod.w(), height);
    }

    private static int at(int[] widths, int index) {
        return index < widths.length ? widths[index] : 0;
    }
}
