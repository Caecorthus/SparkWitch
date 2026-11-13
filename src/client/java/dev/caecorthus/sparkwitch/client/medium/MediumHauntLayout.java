package dev.caecorthus.sparkwitch.client.medium;

/**
 * Pure geometry of the 招魂 drawer in Wathe's inventory screen (owner pick 2026-11-13: face tiles): docked below the
 * hotbar block, centred, in the band the NoellesRoles / Magician / Witch Maiden head rows use (the Medium owns none of
 * them and has no shop row). The drawer is as wide as the candidate count needs (2..4 rows of 20 px tiles on a 22 px
 * pitch, capped by the screen width) and pages when they do not all fit. Every rect and hit test lives here, so what
 * is clicked is exactly what is painted; it holds no client types and the drawer passes measured label widths in. GUI
 * px; right and bottom edges are exclusive, like {@code DrawContext.fill}.
 * Wathe 背包界面中招魂抽屉的纯几何（所有者 2026-11-13 选定：头像格）：停靠在热栏块下方并居中，位于 NoellesRoles / 魔术师 /
 * 巫女头像行所用的区域（通灵师没有这些行，也没有商店行）。抽屉宽度由候选人数决定（20 像素头像格、22 像素间距，1..4 行，受屏幕
 * 宽度限制），放不下时分页。所有矩形与命中判定都在此处，保证点击区域与绘制位置一致；不含客户端类型，标签宽度由抽屉测量后传入。
 * 单位为 GUI 像素，右缘与下缘为开区间（与 fill 一致）。
 */
public final class MediumHauntLayout {
    /** Wathe's hotbar strip height; its top is {@code (height - 32) / 2}. / Wathe 热栏条高度，其顶边为 {@code (height - 32) / 2}。 */
    public static final int STRIP_HEIGHT = 32;
    public static final int TOP_GAP = 6;
    public static final int BOTTOM_GAP = 6;
    public static final int SCREEN_MARGIN = 8;
    public static final int TILE = 20;
    public static final int PITCH = 22;
    public static final int LIST_INSET = 8;
    public static final int VIEW_TOP = 25;
    public static final int VIEW_PAD = 3;
    public static final int FOOTER_H = 22;
    public static final int MIN_WIDTH = 236;
    public static final int MAX_ROWS = 4;
    // Footer, counted up from the drawer bottom (the Tarot ledger's footer grammar). / 页脚，自抽屉底边向上计（塔罗台账页脚语法）。
    public static final int FOOTER_RULE_Y = 21;
    public static final int FOOTER_ROW_Y = 17;
    public static final int FOOTER_INSET = 7;
    public static final int FOOTER_TEXT_Y = 3;
    public static final int ARROW_W = 9;
    public static final int ARROW_H = 11;
    public static final int ARROW_Y = 1;
    public static final int PAGE_TEXT_GAP = 3;
    public static final int BUTTON_H = 13;
    /** Play icon + label padding of the confirm tag button. / 确认价签按钮的图标与文字留白。 */
    public static final int BUTTON_LABEL_X = 12;
    public static final int BUTTON_PAD_R = 6;
    /** The message ends this far left of the button; after the pager it starts this far right of it. / 消息与按钮、翻页之间的间隔。 */
    public static final int MESSAGE_GAP = 6;
    public static final int PAGER_MESSAGE_GAP = 8;
    // Title band. / 标题带。
    public static final int TITLE_ICON_X = 7;
    public static final int TITLE_Y = 7;
    public static final int TITLE_X = 18;
    public static final int CHIP_Y = 5;
    public static final int CHIP_PAD = 3;
    public static final int TITLE_GAP = 6;

    private MediumHauntLayout() {
    }

    /**
     * The drawer for {@code count} candidates. {@code minLeft} is the left edge floor: {@link #SCREEN_MARGIN}, or the
     * BEAR headset cell's right edge plus a gap when that cell is shown (the cell reaches into this band).
     * 容纳 {@code count} 名候选人的抽屉。{@code minLeft} 为左缘下限：{@link #SCREEN_MARGIN}，或 BEAR 耳机格显示时取其右缘再加
     * 间隔（耳机格会伸入此区域）。
     */
    public static Layout layout(int screenWidth, int screenHeight, int count, int minLeft) {
        int stripBottom = (screenHeight - STRIP_HEIGHT) / 2 + STRIP_HEIGHT;
        int y = stripBottom + TOP_GAP;
        int avail = screenHeight - BOTTOM_GAP - y;
        int left = Math.max(SCREEN_MARGIN, minLeft);
        int maxWidth = Math.max(0, Math.min(screenWidth - 2 * SCREEN_MARGIN, screenWidth - SCREEN_MARGIN - left));
        int rowsFit = Math.max(1, Math.min(MAX_ROWS,
                (avail - VIEW_TOP - VIEW_PAD - FOOTER_H + (PITCH - TILE)) / PITCH));
        int maxCols = Math.max(1, (maxWidth - 2 * LIST_INSET + (PITCH - TILE)) / PITCH);
        int n = Math.max(0, count);
        // Prefer few rows: the drawer stays a flat strip under the hotbar. / 优先少行：抽屉保持为热栏下方的扁平条。
        int rows = Math.min(rowsFit, Math.max(1, ceilDiv(n, maxCols)));
        int cols = Math.min(maxCols, Math.max(1, ceilDiv(n, rows)));
        int width = Math.min(maxWidth, Math.max(MIN_WIDTH, 2 * LIST_INSET + cols * PITCH - (PITCH - TILE)));
        int height = VIEW_TOP + rows * PITCH - (PITCH - TILE) + VIEW_PAD + FOOTER_H;
        int x = Math.max(left, (screenWidth - width) / 2);
        if (x + width > screenWidth - SCREEN_MARGIN) {
            x = Math.max(left, screenWidth - SCREEN_MARGIN - width);
        }
        int gridWidth = cols * PITCH - (PITCH - TILE);
        int perPage = rows * cols;
        return new Layout(new Rect(x, y, width, height), rows, cols, perPage, Math.max(1, ceilDiv(n, perPage)),
                x + (width - gridWidth) / 2, y + VIEW_TOP);
    }

    private static int ceilDiv(int a, int b) {
        return b <= 0 ? 0 : (a + b - 1) / b;
    }

    public record Rect(int x, int y, int width, int height) {
        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
        }
    }

    /** The drawer frame, its grid and paging. / 抽屉外框、网格与分页。 */
    public record Layout(Rect frame, int rows, int cols, int perPage, int pages, int gridX, int gridY) {
        public int clampPage(int page) {
            return Math.max(0, Math.min(pages - 1, page));
        }

        /** Tile {@code slot} (0-based on the page). / 本页第 {@code slot} 个头像格（从 0 开始）。 */
        public Rect tile(int slot) {
            return new Rect(gridX + (slot % cols) * PITCH, gridY + (slot / cols) * PITCH, TILE, TILE);
        }

        /**
         * The page slot under the pointer, or -1; the 2 px gaps between tiles hit nothing, so a click there never
         * selects a neighbour.
         * 指针下的本页格位，没有则为 -1；格间 2 像素间隔不命中，点在那里绝不会选中相邻的人。
         */
        public int slotAt(double mouseX, double mouseY) {
            int localX = (int) Math.floor(mouseX) - gridX;
            int localY = (int) Math.floor(mouseY) - gridY;
            if (localX < 0 || localY < 0) {
                return -1;
            }
            int col = localX / PITCH;
            int row = localY / PITCH;
            if (col >= cols || row >= rows || localX % PITCH >= TILE || localY % PITCH >= TILE) {
                return -1;
            }
            return row * cols + col;
        }

        public int ruleY() {
            return frame.bottom() - FOOTER_RULE_Y;
        }

        public int footerRowY() {
            return frame.bottom() - FOOTER_ROW_Y;
        }

        public int footerTextY() {
            return footerRowY() + FOOTER_TEXT_Y;
        }

        /** Shown only when {@link #pages} is above 1. / 仅在页数大于 1 时显示。 */
        public Rect previousArrow() {
            return new Rect(frame.x() + FOOTER_INSET, footerRowY() + ARROW_Y, ARROW_W, ARROW_H);
        }

        public int pageTextX() {
            return previousArrow().right() + PAGE_TEXT_GAP;
        }

        public Rect nextArrow(int pageTextWidth) {
            return new Rect(pageTextX() + pageTextWidth + PAGE_TEXT_GAP, footerRowY() + ARROW_Y, ARROW_W, ARROW_H);
        }

        /** The confirm tag button, right-aligned in the footer row. / 确认价签按钮，在页脚行右对齐。 */
        public Rect button(int labelWidth) {
            int width = BUTTON_LABEL_X + labelWidth + BUTTON_PAD_R;
            return new Rect(frame.right() - FOOTER_INSET - width, footerRowY(), width, BUTTON_H);
        }

        /**
         * Left edge of the footer message: after the pager when there is one, else at the footer inset.
         * 页脚消息的左缘：有翻页时在翻页之后，否则在页脚内边距处。
         */
        public int messageLeft(int pageTextWidth) {
            return pages > 1 ? nextArrow(pageTextWidth).right() + PAGER_MESSAGE_GAP : frame.x() + FOOTER_INSET;
        }
    }
}
