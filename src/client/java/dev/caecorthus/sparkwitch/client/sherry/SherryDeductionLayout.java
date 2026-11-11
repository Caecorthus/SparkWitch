package dev.caecorthus.sparkwitch.client.sherry;

import java.util.function.ToIntFunction;

/**
 * Pure geometry of 橘雪莉's 推演 panel: the centred Harpy Express frame, the title band with its price chip, the rule
 * line, the ruled candidate grid (16 px face + name, 3 columns, 2 when names would be cut and it costs no extra
 * page), paging, the footer (status pill, Cancel, Confirm) and hit-testing. {@code SherryDeductionScreen} paints
 * exactly these rectangles and resolves clicks through {@link #hit}, so what is drawn is what is clicked. No client
 * types: text widths are measured by the screen and passed in. Units are GUI pixels; right and bottom edges are
 * exclusive, like {@code DrawContext.fill}.
 * 橘雪莉推演面板的纯几何：居中的哈比特快外框、带价格徽章的标题带、规则行、带横线的候选网格（16 像素头像 + 名字，
 * 默认 3 列；名字会被截断且不增加页数时改为 2 列）、翻页、页脚（状态牌、取消、确认）与命中判定。
 * {@code SherryDeductionScreen} 严格按这些矩形绘制并通过 {@link #hit} 判定点击，绘制即命中。不含客户端类型，
 * 文本宽度由界面测量后传入。单位为 GUI 像素，右缘与下缘为开区间（与 {@code DrawContext.fill} 一致）。
 */
public final class SherryDeductionLayout {
    // ---- panel ----
    public static final int PANEL_W = 300;
    /** Below this the footer no longer holds both buttons; the panel keeps it and overflows. 低于此宽度页脚放不下两个按钮。 */
    public static final int PANEL_MIN_W = 196;
    /** Air between the panel and the screen edge on each side. 面板与屏幕边缘每侧的空隙。 */
    public static final int SCREEN_MARGIN = 4;
    /** Frame outline (1) + bevel (1) + ring (1): the body starts here. 外框描边、斜面与描金线各 1 像素。 */
    public static final int BODY_INSET = 3;

    // ---- title band ----
    public static final int BAND_TOP = 3;
    public static final int BAND_BOTTOM = 19;
    /** Band plus its two rule lines: the body below starts here. 标题带及其两条刻线之下，正文自此开始。 */
    public static final int BAND_END = 21;
    public static final int TITLE_ICON_X = 7;
    public static final int TITLE_ICON_Y = 6;
    public static final int TITLE_X = 20;
    public static final int TITLE_Y = 7;
    public static final int PRICE_RIGHT = 6;
    public static final int PRICE_Y = 5;
    public static final int PRICE_H = 11;
    public static final int PRICE_PAD = 4;
    public static final int PRICE_TEXT_Y = 2;
    /** Minimum air between the title and the price chip. 标题与价格徽章之间的最小间隔。 */
    public static final int TITLE_PRICE_GAP = 8;

    // ---- rule line ----
    public static final int HINT_X = 8;
    public static final int HINT_Y = 25;
    public static final int HINT_LINE_H = 10;
    public static final int HINT_MAX_LINES = 2;
    /** The 2 px rule under the last hint line, then the grid. 最后一行规则文字下方的 2 像素刻线，其后是网格。 */
    public static final int HINT_RULE_GAP = 1;
    public static final int VIEW_GAP = 4;

    // ---- grid (cell-relative) ----
    public static final int VIEW_X = 6;
    public static final int ROW_PITCH = 20;
    public static final int MAX_ROWS = 7;
    /** A short roster still gets this many rows, so the panel never collapses to a strip. 名单很短时也保留的行数。 */
    public static final int MIN_ROWS = 3;
    public static final int MAX_COLS = 3;
    public static final int COL_GAP = 4;
    /** A column narrower than this is not opened. 窄于此值不再增加列。 */
    public static final int MIN_CELL_W = 84;
    /** The selected row's bar on its left edge. 选中行左缘的竖条。 */
    public static final int BAR_W = 2;
    public static final int FACE_FRAME_X = 3;
    public static final int FACE_FRAME_Y = 1;
    public static final int FACE_FRAME_SIZE = 18;
    public static final int FACE_X = 4;
    public static final int FACE_Y = 2;
    public static final int FACE_SIZE = 16;
    public static final int NAME_X = 25;
    /** A name alone on its row is centred on the face. 单独一行的名字与头像居中对齐。 */
    public static final int NAME_Y = 6;
    /** Name and mark of a row whose chip moved under the name (see {@link Grid#chipsBelow}). 徽记移到名字下方时的两行位置。 */
    public static final int NAME_Y_STACKED = 2;
    public static final int MARK_Y = 11;
    public static final int MARK_ICON_Y = 12;
    public static final int CELL_PAD_R = 2;
    /** The ruled line under a row starts here and sits on the row's last pixel. 行下横线的起点；位于行的最后一像素。 */
    public static final int ROW_RULE_X = 24;
    public static final int CHIP_H = 11;
    public static final int CHIP_Y = 4;
    public static final int CHIP_PAD = 3;
    public static final int CHIP_GAP = 3;
    public static final int CHIP_ICON_W = 5;
    public static final int CHIP_ICON_GAP = 2;
    public static final int CHIP_ICON_Y = 3;
    public static final int CHIP_TEXT_Y = 2;
    public static final int VIEW_BOTTOM_PAD = 3;

    // ---- pager (its own row under the grid, only while there is more than one page) ----
    public static final int PAGER_H = 13;
    public static final int ARROW_SIZE = 11;
    public static final int PAGER_GAP = 6;
    public static final int PAGER_TEXT_Y = 2;

    // ---- footer (counted up from the panel bottom) ----
    public static final int FOOTER_H = 23;
    public static final int FOOTER_RULE_Y = 22;
    public static final int FOOTER_ROW_Y = 17;
    public static final int FOOTER_X = 7;
    public static final int PILL_Y = 16;
    public static final int PILL_H = 11;
    public static final int PILL_PAD = 3;
    public static final int PILL_ICON_W = 5;
    public static final int PILL_ICON_GAP = 3;
    public static final int PILL_ICON_Y = 3;
    public static final int PILL_TEXT_Y = 2;
    /** A status pill with less text room than this is dropped. 文字空间小于此值时不显示状态牌。 */
    public static final int PILL_MIN_TEXT = 16;
    /** Minimum air between the status pill and the buttons. 状态牌与按钮之间的最小间隔。 */
    public static final int FOOTER_GAP = 6;
    public static final int BUTTON_H = 13;
    public static final int BUTTON_GAP = 5;
    public static final int BUTTON_PAD = 7;
    public static final int BUTTON_ICON_X = 6;
    public static final int BUTTON_ICON_W = 5;
    public static final int BUTTON_ICON_Y = 4;
    /** Label x of a button that carries an icon. 带图标按钮的文字起点。 */
    public static final int BUTTON_LABEL_X = 14;
    public static final int BUTTON_TEXT_Y = 3;

    // ---- tooltip ----
    public static final int TIP_OFFSET = 9;
    public static final int TIP_PAD = 4;
    public static final int TIP_LINE_H = 10;
    public static final int TIP_MAX_W = 170;

    private static final String ELLIPSIS = "…";

    private SherryDeductionLayout() {
    }

    // ================================================================ panel

    /**
     * Columns, rows and paging of the candidate grid. {@code rows} is what one page shows (and what the panel is
     * tall for); {@code perPage} = columns x rows.
     * 候选网格的列、行与分页。{@code rows} 为一页显示的行数（也决定面板高度）；{@code perPage} = 列 × 行。
     */
    public record Grid(int columns, int rows, int cellWidth, int perPage, int pageCount, boolean chipsBelow) {
        public boolean paged() {
            return pageCount > 1;
        }

        /** Width left for a name in this grid; {@code chipWidth} 0 = the row has no chip. 本网格中留给名字的宽度。 */
        public int nameMax(int chipWidth) {
            return SherryDeductionLayout.nameMax(cellWidth, chipsBelow ? 0 : chipWidth);
        }
    }

    /**
     * The laid-out panel in screen coordinates. {@code pagerY} is the top of the pager's arrows and only meaningful
     * while {@link Grid#paged}.
     * 以屏幕坐标表示的面板布局。{@code pagerY} 为翻页箭头的顶边，仅在 {@link Grid#paged} 时有意义。
     */
    public record Panel(Rect frame, int hintLines, int hintRuleY, Rect view, Grid grid, int pagerY) {
        public int centerX() {
            return frame.x() + frame.width() / 2;
        }

        public int footerRuleY() {
            return frame.bottom() - FOOTER_RULE_Y;
        }
    }

    public static int panelWidth(int screenWidth) {
        return Math.max(PANEL_MIN_W, Math.min(PANEL_W, screenWidth - 2 * SCREEN_MARGIN));
    }

    /** Wrap width of the rule line. 规则行的换行宽度。 */
    public static int hintBudget(int panelWidth) {
        return Math.max(0, panelWidth - 2 * HINT_X);
    }

    private static int clampHintLines(int hintLines) {
        return Math.clamp(hintLines, 1, HINT_MAX_LINES);
    }

    /** Panel-relative y of the 2 px rule under the rule line. 规则行下方 2 像素刻线的面板内 y。 */
    public static int hintRuleY(int hintLines) {
        return HINT_Y + clampHintLines(hintLines) * HINT_LINE_H + HINT_RULE_GAP;
    }

    /** Panel-relative top of the grid. 网格顶边的面板内 y。 */
    public static int viewTop(int hintLines) {
        return hintRuleY(hintLines) + VIEW_GAP;
    }

    /**
     * Rows a page can hold on this screen: {@link #MAX_ROWS} when there is room, fewer on a small scaled screen,
     * never below one.
     * 当前屏幕上一页能容纳的行数：空间足够时为 {@link #MAX_ROWS}，小缩放屏幕上相应减少，至少一行。
     */
    public static int rowsThatFit(int screenHeight, int hintLines, boolean paged) {
        int room = screenHeight - 2 * SCREEN_MARGIN - viewTop(hintLines) - VIEW_BOTTOM_PAD
                - (paged ? PAGER_H : 0) - FOOTER_H;
        return Math.clamp(room / ROW_PITCH, 1, MAX_ROWS);
    }

    public static int maxColumns(int viewWidth) {
        return Math.clamp((viewWidth + COL_GAP) / (MIN_CELL_W + COL_GAP), 1, MAX_COLS);
    }

    public static int cellWidth(int viewWidth, int columns) {
        return Math.max(0, (viewWidth - (columns - 1) * COL_GAP) / Math.max(1, columns));
    }

    /** Width left for the name in a cell whose chip (0 = none) is {@code chipWidth} wide. 单元内留给名字的宽度。 */
    public static int nameMax(int cellWidth, int chipWidth) {
        return Math.max(0, cellWidth - NAME_X - CELL_PAD_R - (chipWidth > 0 ? chipWidth + CHIP_GAP : 0));
    }

    /** What a row needs to show its whole name next to its chip. 一行完整显示名字与徽记所需的宽度。 */
    public static int neededWidth(int nameWidth, int chipWidth) {
        return nameWidth + (chipWidth > 0 ? chipWidth + CHIP_GAP : 0);
    }

    public static int pageCount(int entries, int perPage) {
        return Math.max(1, (entries + Math.max(1, perPage) - 1) / Math.max(1, perPage));
    }

    private static int pages(int count, int columns, int screenHeight, int hintLines) {
        int free = rowsThatFit(screenHeight, hintLines, false);
        return count <= columns * free
                ? 1 : pageCount(count, columns * rowsThatFit(screenHeight, hintLines, true));
    }

    /**
     * The grid for {@code count} candidates whose widest name is {@code widestName} px and whose widest marked row
     * needs {@code widestMarked} px for name and chip side by side (see {@link #neededWidth}; 0 = no marked row).
     * Three columns by default; one column fewer when a name would be cut and the narrower grid does not add a page,
     * so short rosters show whole names and long rosters stay dense. Chips sit right-aligned beside the name while
     * every marked name still fits whole; otherwise all of them move under their names, so a mark never costs a name
     * its letters. The page is only as tall as the roster needs, and grows to the full height once there is more than
     * one page.
     * {@code count} 名候选的网格：最宽的名字为 {@code widestName} 像素，带徽记的行中名字与徽记并排最宽需要
     * {@code widestMarked} 像素（见 {@link #neededWidth}；0 表示没有带徽记的行）。默认三列；有名字会被截断且减少一列不会
     * 增加页数时少用一列，短名单因此显示完整名字，长名单保持紧凑。只要每个带徽记的名字都能完整显示，徽记就在名字右侧
     * 右对齐；否则全部移到名字下方，徽记不会挤掉名字。页面高度只取名单所需，出现多页后固定为整页高度。
     */
    public static Grid grid(int viewWidth, int screenHeight, int hintLines, int count, int widestName,
                            int widestMarked) {
        int entries = Math.max(0, count);
        int columns = maxColumns(viewWidth);
        if (columns > 2 && widestName > nameMax(cellWidth(viewWidth, columns), 0)
                && pages(entries, columns - 1, screenHeight, hintLines)
                <= pages(entries, columns, screenHeight, hintLines)) {
            columns--;
        }
        int free = rowsThatFit(screenHeight, hintLines, false);
        boolean paged = entries > columns * free;
        int rows = paged
                ? rowsThatFit(screenHeight, hintLines, true)
                : Math.clamp((entries + columns - 1) / columns, Math.min(MIN_ROWS, free), free);
        int perPage = columns * rows;
        int cellWidth = cellWidth(viewWidth, columns);
        return new Grid(columns, rows, cellWidth, perPage, paged ? pageCount(entries, perPage) : 1,
                widestMarked > nameMax(cellWidth, 0));
    }

    /**
     * The whole panel, centred. {@code hintLines} is the wrapped rule line (1 or 2).
     * 整个面板，居中放置。{@code hintLines} 为规则行换行后的行数（1 或 2）。
     */
    public static Panel panel(int screenWidth, int screenHeight, int count, int widestName, int widestMarked,
                              int hintLines) {
        int lines = clampHintLines(hintLines);
        int width = panelWidth(screenWidth);
        int viewWidth = width - 2 * VIEW_X;
        Grid grid = grid(viewWidth, screenHeight, lines, count, widestName, widestMarked);
        int viewHeight = grid.rows() * ROW_PITCH;
        int height = viewTop(lines) + viewHeight + VIEW_BOTTOM_PAD + (grid.paged() ? PAGER_H : 0) + FOOTER_H;
        int x = (screenWidth - width) / 2;
        int y = Math.max(0, (screenHeight - height) / 2);
        Rect view = new Rect(x + VIEW_X, y + viewTop(lines), viewWidth, viewHeight);
        return new Panel(new Rect(x, y, width, height), lines, y + hintRuleY(lines), view, grid,
                view.bottom() + VIEW_BOTTOM_PAD - 1);
    }

    // ================================================================ grid cells and paging

    /** Cell {@code slot} on the page, row-major (left to right, then down). 页内第 slot 个单元，按行优先排列。 */
    public static Rect cell(Panel panel, int slot) {
        Grid grid = panel.grid();
        int column = Math.floorMod(slot, grid.columns());
        int row = Math.floorDiv(slot, grid.columns());
        return new Rect(panel.view().x() + column * (grid.cellWidth() + COL_GAP),
                panel.view().y() + row * ROW_PITCH, grid.cellWidth(), ROW_PITCH);
    }

    public static int clampPage(Grid grid, int page) {
        return Math.clamp(page, 0, grid.pageCount() - 1);
    }

    /** Roster index of the page's first cell. 该页第一个单元对应的名单下标。 */
    public static int firstIndex(Grid grid, int page) {
        return clampPage(grid, page) * grid.perPage();
    }

    /** Cells shown on {@code page} for a roster of {@code count}. 名单共 count 人时该页显示的单元数。 */
    public static int slotsOnPage(Grid grid, int count, int page) {
        return Math.clamp((long) count - firstIndex(grid, page), 0, grid.perPage());
    }

    /** The page that shows roster index {@code index}. 显示名单第 index 人的页码。 */
    public static int pageOf(Grid grid, int index) {
        return clampPage(grid, Math.max(0, index) / Math.max(1, grid.perPage()));
    }

    /** 3 + icon + 2 + label + 3. */
    public static int chipWidth(int labelWidth) {
        return CHIP_PAD + markWidth(labelWidth) + CHIP_PAD;
    }

    /** A mark without its chip box: icon + 2 + label. 不带外框的徽记：图标 + 2 + 文字。 */
    public static int markWidth(int labelWidth) {
        return CHIP_ICON_W + CHIP_ICON_GAP + labelWidth;
    }

    /** The row's chip, right-aligned in its cell. 行内徽记，在单元内右对齐。 */
    public static Rect chip(Rect cell, int chipWidth) {
        return new Rect(cell.right() - CELL_PAD_R - chipWidth, cell.y() + CHIP_Y, chipWidth, CHIP_H);
    }

    // ================================================================ title bar

    /** The price chip and the room left for the title. 价格徽章以及留给标题的宽度。 */
    public record TitleBar(Rect price, int priceTextX, int textY, int titleX, int titleMaxWidth) {
    }

    public static TitleBar titleBar(Panel panel, int priceTextWidth) {
        Rect frame = panel.frame();
        int chipWidth = PRICE_PAD + priceTextWidth + PRICE_PAD;
        Rect chip = new Rect(frame.right() - PRICE_RIGHT - chipWidth, frame.y() + PRICE_Y, chipWidth, PRICE_H);
        int titleX = frame.x() + TITLE_X;
        return new TitleBar(chip, chip.x() + PRICE_PAD, frame.y() + TITLE_Y, titleX,
                Math.max(0, chip.x() - TITLE_PRICE_GAP - titleX));
    }

    // ================================================================ pager

    /** Prev arrow, counter, next arrow, centred under the grid. 上一页箭头、页码、下一页箭头，在网格下方居中。 */
    public record Pager(boolean shown, Rect prev, Rect next, int textX, int textY) {
        public static final Pager HIDDEN = new Pager(false, Rect.EMPTY, Rect.EMPTY, 0, 0);
    }

    public static Pager pager(Panel panel, int pageTextWidth) {
        if (!panel.grid().paged()) {
            return Pager.HIDDEN;
        }
        int total = ARROW_SIZE + PAGER_GAP + pageTextWidth + PAGER_GAP + ARROW_SIZE;
        int x = panel.centerX() - total / 2;
        int y = panel.pagerY();
        int textX = x + ARROW_SIZE + PAGER_GAP;
        return new Pager(true, new Rect(x, y, ARROW_SIZE, ARROW_SIZE),
                new Rect(textX + pageTextWidth + PAGER_GAP, y, ARROW_SIZE, ARROW_SIZE), textX, y + PAGER_TEXT_Y);
    }

    // ================================================================ footer

    /**
     * Status pill on the left (empty rect = dropped for lack of room), Cancel and Confirm on the right.
     * 左侧状态牌（空矩形表示空间不足而省略），右侧为取消与确认。
     */
    public record Footer(Rect pill, int pillTextMax, Rect cancel, Rect confirm) {
    }

    public static int buttonWidth(int labelWidth, boolean icon) {
        return (icon ? BUTTON_LABEL_X : BUTTON_PAD) + labelWidth + BUTTON_PAD;
    }

    /** 3 + icon + 3 + label + 3. */
    public static int pillWidth(int labelWidth) {
        return PILL_PAD + PILL_ICON_W + PILL_ICON_GAP + labelWidth + PILL_PAD;
    }

    /**
     * The buttons keep their label widths; the pill takes what is left and its text is cut to {@code pillTextMax}.
     * 按钮保持其文字宽度；状态牌使用剩余空间，其文字截断为 {@code pillTextMax}。
     */
    public static Footer footer(Panel panel, int pillLabelWidth, int cancelLabelWidth, int confirmLabelWidth) {
        Rect frame = panel.frame();
        int buttonY = frame.bottom() - FOOTER_ROW_Y;
        int confirmWidth = buttonWidth(confirmLabelWidth, true);
        int cancelWidth = buttonWidth(cancelLabelWidth, false);
        Rect confirm = new Rect(frame.right() - FOOTER_X - confirmWidth, buttonY, confirmWidth, BUTTON_H);
        Rect cancel = new Rect(confirm.x() - BUTTON_GAP - cancelWidth, buttonY, cancelWidth, BUTTON_H);
        int pillX = frame.x() + FOOTER_X;
        int room = cancel.x() - FOOTER_GAP - pillX;
        if (room < pillWidth(PILL_MIN_TEXT)) {
            return new Footer(Rect.EMPTY, 0, cancel, confirm);
        }
        int width = Math.min(pillWidth(pillLabelWidth), room);
        return new Footer(new Rect(pillX, frame.bottom() - PILL_Y, width, PILL_H),
                width - pillWidth(0), cancel, confirm);
    }

    // ================================================================ hit test

    public enum HitKind {
        NONE,
        /** A candidate row; {@link Hit#index} is its page slot. 候选行；index 为页内序号。 */
        ROW,
        PREV,
        NEXT,
        CANCEL,
        CONFIRM,
        /** The price chip (tooltip only). 价格徽章（仅提示）。 */
        PRICE
    }

    public record Hit(HitKind kind, int index) {
        public static final Hit NONE = new Hit(HitKind.NONE, -1);
    }

    /**
     * What is under the pointer. A row is hit across its whole cell and the gap between two columns belongs to no
     * row; arrows are reported whether or not the page can turn (the screen decides).
     * 指针下的元素。行以整个单元为命中范围，列间隔不属于任何行；箭头无论能否翻页都会返回（由界面决定是否生效）。
     */
    public static Hit hit(Panel panel, TitleBar bar, Pager pager, Footer footer, int slotsOnPage,
                          double mouseX, double mouseY) {
        if (!panel.frame().contains(mouseX, mouseY)) {
            return Hit.NONE;
        }
        if (footer.confirm().contains(mouseX, mouseY)) {
            return new Hit(HitKind.CONFIRM, -1);
        }
        if (footer.cancel().contains(mouseX, mouseY)) {
            return new Hit(HitKind.CANCEL, -1);
        }
        if (pager.shown()) {
            if (pager.prev().contains(mouseX, mouseY)) {
                return new Hit(HitKind.PREV, -1);
            }
            if (pager.next().contains(mouseX, mouseY)) {
                return new Hit(HitKind.NEXT, -1);
            }
        }
        if (bar.price().contains(mouseX, mouseY)) {
            return new Hit(HitKind.PRICE, -1);
        }
        int slot = slotAt(panel, slotsOnPage, mouseX, mouseY);
        return slot >= 0 ? new Hit(HitKind.ROW, slot) : Hit.NONE;
    }

    /** Page slot under the pointer, or -1. 指针下的页内序号；没有时为 -1。 */
    public static int slotAt(Panel panel, int slotsOnPage, double mouseX, double mouseY) {
        Rect view = panel.view();
        if (!view.contains(mouseX, mouseY)) {
            return -1;
        }
        Grid grid = panel.grid();
        int pitch = grid.cellWidth() + COL_GAP;
        int column = (int) ((mouseX - view.x()) / pitch);
        if (column >= grid.columns() || mouseX - view.x() - column * pitch >= grid.cellWidth()) {
            return -1;
        }
        int slot = (int) ((mouseY - view.y()) / ROW_PITCH) * grid.columns() + column;
        return slot < slotsOnPage ? slot : -1;
    }

    // ================================================================ text

    /**
     * {@code text} cut to {@code maxWidth} with a trailing "…", measured by {@code width}; unchanged when it fits,
     * empty when not even the ellipsis fits. Cuts on code points, never inside a surrogate pair.
     * 将 {@code text} 截断到 {@code maxWidth} 并以「…」结尾，宽度由 {@code width} 测量；放得下时原样返回，连省略号都放不下时
     * 返回空串。按码点截断，不会切开代理对。
     */
    public static String trim(String text, int maxWidth, ToIntFunction<String> width) {
        if (maxWidth <= 0) {
            return "";
        }
        if (width.applyAsInt(text) <= maxWidth) {
            return text;
        }
        int ellipsis = width.applyAsInt(ELLIPSIS);
        if (ellipsis > maxWidth) {
            return "";
        }
        int end = 0;
        for (int next = 0; next < text.length(); ) {
            next += Character.charCount(text.codePointAt(next));
            if (width.applyAsInt(text.substring(0, next)) + ellipsis > maxWidth) {
                break;
            }
            end = next;
        }
        return text.substring(0, end) + ELLIPSIS;
    }

    // ================================================================ tooltip

    /**
     * Tooltip content box near the pointer, flipped and clamped so the box (with TIP_PAD) stays on screen.
     * 指针附近的提示框内容区，必要时翻转并夹紧，使提示框（含 TIP_PAD）留在屏幕内。
     */
    public static Rect tooltip(int screenWidth, int screenHeight, int mouseX, int mouseY, int width, int height) {
        int x = mouseX + TIP_OFFSET;
        if (x + width + TIP_PAD > screenWidth) {
            x = mouseX - TIP_OFFSET - width;
        }
        int y = mouseY - TIP_OFFSET;
        x = Math.max(TIP_PAD, Math.min(x, screenWidth - TIP_PAD - width));
        y = Math.max(TIP_PAD, Math.min(y, screenHeight - TIP_PAD - height));
        return new Rect(x, y, width, height);
    }

    public static int tooltipHeight(int lines) {
        return Math.max(0, lines * TIP_LINE_H - 2);
    }

    // ================================================================ rect

    public record Rect(int x, int y, int width, int height) {
        public static final Rect EMPTY = new Rect(0, 0, 0, 0);

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean isEmpty() {
            return width <= 0 || height <= 0;
        }

        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }
}
