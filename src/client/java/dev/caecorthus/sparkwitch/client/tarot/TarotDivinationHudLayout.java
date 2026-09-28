package dev.caecorthus.sparkwitch.client.tarot;

import java.util.List;

/**
 * Pure layout of the Tarot Reader divination table: row order, change trends, count labels and integer GUI
 * geometry. It holds no client types; the renderer measures text and passes the widths in.
 * 塔罗牌师占卜表的纯布局：行顺序、变化趋势、人数文本与整数 GUI 几何。不含客户端类型，文本宽度由渲染器测量后传入。
 */
public final class TarotDivinationHudLayout {
    /** Title y, one row below Wathe's money row (drawn at y 6). 标题 y，位于 Wathe 金币行（绘制于 y 6）下方。 */
    public static final int HEADER_Y = 20;
    /** The 7x9 card icon covers y 19..27, so its centre row matches the title glyphs. 卡牌图标中心与标题字形对齐。 */
    public static final int ICON_Y = 19;
    public static final int ICON_WIDTH = 7;
    /** Title and faction names share this x offset from the table's left edge. 标题与阵营名共用此左侧偏移。 */
    public static final int NAME_X = ICON_WIDTH + 3;
    /** The 5-wide pip starts at left + 1, so its centre column is the icon's centre column. 圆点中心列与图标中心列对齐。 */
    public static final int PIP_INSET = 1;
    public static final int PIP_Y_OFFSET = 1;
    public static final int TREND_WIDTH = 5;
    public static final int TREND_GAP = 3;
    public static final int TREND_Y_OFFSET = 2;
    /** Resource-pack safety caps; longer text is ellipsized. 资源包安全上限，超出部分以省略号截断。 */
    public static final int NAME_MAX_WIDTH = 64;
    public static final int TITLE_MAX_WIDTH = 96;
    /**
     * The count column is never narrower than bold "00", so the table keeps its width for counts 0..99.
     * 人数列至少与粗体 "00" 等宽，人数在 0..99 之间变化时表格宽度不变。
     */
    public static final String COUNT_RESERVE_TEXT = "00";
    static final int MAX_SHOWN_COUNT = 99;

    private static final int RIGHT_EDGE_INSET = 5;
    private static final int COLUMN_GAP = 10;
    private static final int SEPARATOR_Y_OFFSET = 10;
    private static final int ROWS_Y_OFFSET = 4;
    private static final int ROW_PITCH = 11;
    /** Vanilla TextRenderer.fontHeight. 原版字体行高。 */
    private static final int LINE_HEIGHT = 9;
    private static final int BACKING_PAD_LEFT = 3;
    private static final int BACKING_PAD_RIGHT = 2;
    private static final int BACKING_PAD_TOP = 3;
    private static final int BACKING_PAD_BOTTOM = 2;
    private static final int ROW_COUNT = FactionSlot.values().length;

    private TarotDivinationHudLayout() {
    }

    /**
     * Rows in the owner-approved order; {@code previous} is the snapshot the latest purchase replaced, or null.
     * 按确认的阵营顺序生成各行；{@code previous} 为最近一次购买所替换的快照，可为 null。
     */
    public static List<Row> rows(
            TarotDivinationSnapshotState.Snapshot current,
            TarotDivinationSnapshotState.Snapshot previous
    ) {
        boolean compare = previous != null;
        return List.of(
                new Row(FactionSlot.CIVILIAN, current.civilianCount(),
                        compare ? Trend.of(previous.civilianCount(), current.civilianCount()) : Trend.NONE),
                new Row(FactionSlot.KILLER, current.killerCount(),
                        compare ? Trend.of(previous.killerCount(), current.killerCount()) : Trend.NONE),
                new Row(FactionSlot.NEUTRAL, current.neutralCount(),
                        compare ? Trend.of(previous.neutralCount(), current.neutralCount()) : Trend.NONE),
                new Row(FactionSlot.WITCH, current.witchCount(),
                        compare ? Trend.of(previous.witchCount(), current.witchCount()) : Trend.NONE)
        );
    }

    public static String displayCount(int count) {
        int shown = Math.max(0, count);
        return shown > MAX_SHOWN_COUNT ? MAX_SHOWN_COUNT + "+" : Integer.toString(shown);
    }

    /** Only 1..99 is bold; zero and "99+" use the regular weight. 仅 1..99 使用粗体；0 与 "99+" 使用常规字重。 */
    public static boolean boldCount(int count) {
        return count > 0 && count <= MAX_SHOWN_COUNT;
    }

    /**
     * Table geometry for measured text widths. {@code countWidth} is the widest shown count and
     * {@code countReserveWidth} the width of bold {@link #COUNT_RESERVE_TEXT}; the count column is the larger of
     * the two, so counts 0..99 never move the table. The table grows leftwards from a fixed right edge and is pinned
     * inside the left screen edge on very narrow windows.
     * 按测得的文本宽度计算表格几何。{@code countWidth} 为最宽人数文本，{@code countReserveWidth} 为粗体
     * {@link #COUNT_RESERVE_TEXT} 的宽度；人数列取两者较大值，人数在 0..99 间变化不会移动表格。表格自固定右缘
     * 向左扩展，窗口极窄时固定在屏幕左缘内侧。
     */
    public static Geometry geometry(
            int screenWidth,
            int titleWidth,
            int nameWidth,
            int countWidth,
            int countReserveWidth
    ) {
        int countColumn = Math.max(countWidth, countReserveWidth);
        int tableWidth = Math.max(NAME_X + nameWidth + COLUMN_GAP + countColumn, NAME_X + titleWidth);
        int right = Math.max(screenWidth - RIGHT_EDGE_INSET, BACKING_PAD_LEFT + tableWidth);
        int left = right - tableWidth;
        int separatorY = HEADER_Y + SEPARATOR_Y_OFFSET;
        int firstRowY = separatorY + ROWS_Y_OFFSET;
        int lastRowY = firstRowY + (ROW_COUNT - 1) * ROW_PITCH;
        return new Geometry(
                left,
                right,
                separatorY,
                firstRowY,
                left - BACKING_PAD_LEFT,
                HEADER_Y - BACKING_PAD_TOP,
                right + BACKING_PAD_RIGHT,
                lastRowY + LINE_HEIGHT + BACKING_PAD_BOTTOM
        );
    }

    public enum FactionSlot {
        CIVILIAN,
        KILLER,
        NEUTRAL,
        WITCH
    }

    public enum Trend {
        NONE,
        UP,
        DOWN;

        static Trend of(int previous, int current) {
            int compared = Integer.compare(Math.max(0, current), Math.max(0, previous));
            return compared > 0 ? UP : compared < 0 ? DOWN : NONE;
        }
    }

    public record Row(FactionSlot faction, int count, Trend trend) {
    }

    /**
     * Exclusive right/bottom edges, like {@code DrawContext.fill}. 右缘与下缘为开区间，与 fill 一致。
     */
    public record Geometry(
            int left,
            int right,
            int separatorY,
            int firstRowY,
            int backingLeft,
            int backingTop,
            int backingRight,
            int backingBottom
    ) {
        public int rowY(int rowIndex) {
            return firstRowY + rowIndex * ROW_PITCH;
        }
    }
}
