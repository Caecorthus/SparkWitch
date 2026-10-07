package dev.caecorthus.sparkwitch.client.blackraven;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometry of the Black Raven Perception Ledger: panel, title band and disguise chip, tabs, the ruled
 * perceived-identity grid (Tab A), the sectioned and scrolling disguise list (Tab B), footer status, revert button
 * and pager, plus the hit tests. {@code BlackRavenLedgerBookScreen} paints with exactly these rects and hit-tests
 * through {@link #hitPerceived} / {@link #hitAbsent}, so what is painted is what is clicked. No client types: the
 * screen measures text and passes the widths in. GUI px; right and bottom edges are exclusive, like
 * {@code DrawContext.fill}.
 * 黑羽鸦感知册的纯几何布局：面板、标题带与伪装徽章、页签、带横线的已感知身份网格（Tab A）、分区可滚动的伪装名单
 * （Tab B）、页脚状态、恢复按钮与翻页，以及命中判定。{@code BlackRavenLedgerBookScreen} 严格按这些矩形绘制并通过
 * {@link #hitPerceived} / {@link #hitAbsent} 判定点击，绘制即命中。不含客户端类型，文本宽度由界面测量后传入。
 * 单位为 GUI 像素，右缘与下缘为开区间（与 fill 一致）。
 */
public final class BlackRavenLedgerLayout {
    // ---- panel ----
    public static final int PANEL_MAX_W = 304;
    public static final int PANEL_MAX_H = 206;
    /** Air between the panel and the screen edge on each side. 面板与屏幕边缘每侧的空隙。 */
    public static final int SCREEN_MARGIN = 4;
    /** Frame outline (1) + bevel (1) + ring (1): BODY content starts here. 外框描边、斜面与描金线各 1 像素。 */
    public static final int BODY_INSET = 3;

    // ---- title band ----
    public static final int BAND_TOP = 3;
    public static final int BAND_BOTTOM = 19;
    public static final int TITLE_ICON_X = 7;
    public static final int TITLE_ICON_Y = 6;
    public static final int TITLE_X = 18;
    public static final int TITLE_Y = 7;
    public static final int CHIP_RIGHT = 7;
    public static final int CHIP_GAP = 4;
    public static final int GEM_SIZE = 5;
    public static final int GEM_TEXT_GAP = 3;
    /** Chip gem y, from the title text y. 徽章宝石相对标题文字的 y 偏移。 */
    public static final int CHIP_GEM_Y = 1;
    public static final int CHIP_DIVIDER_H = 8;
    /** Minimum air between the title and the disguise chip. 标题与伪装徽章之间的最小间隔。 */
    public static final int TITLE_CHIP_GAP = 8;
    /** A chip role name narrower than this is dropped rather than shown as a stub. 窄于此值的徽章职业名直接省略。 */
    public static final int CHIP_ROLE_MIN_W = 24;

    // ---- tabs ----
    public static final int TABS_X = 6;
    public static final int TAB_Y = 23;
    public static final int TAB_H = 14;
    public static final int TAB_GAP = 2;
    /** Label inset each side. 标签两侧内边距。 */
    public static final int TAB_PAD = 7;
    public static final int TAB_COUNT_GAP = 5;
    public static final int TAB_TEXT_Y = 3;
    /** The rule under the tab row (2 px), from the panel top. 页签行下方的刻线（2 像素），自面板顶边起。 */
    public static final int TAB_RULE_Y = 37;
    public static final int KEYCAP_PAD = 3;
    public static final int KEYCAP_H = 11;
    public static final int KEYCAP_Y = 1;
    public static final int HINT_GAP = 3;
    /** Minimum air between the last tab and the Tab-key hint. 最后一个页签与 Tab 键提示之间的最小间隔。 */
    public static final int TAB_HINT_GAP = 8;

    // ---- view ----
    public static final int VIEW_X = 7;
    public static final int VIEW_TOP = 40;
    /** View bottom = panel bottom - FOOTER_H. 视口底边 = 面板底边 - FOOTER_H。 */
    public static final int FOOTER_H = 23;

    // ---- Tab A: perceived grid (cell-relative) ----
    public static final int GRID_TOP_PAD = 1;
    public static final int PERCEIVED_ROWS = 7;
    public static final int PERCEIVED_PITCH = 20;
    public static final int PERCEIVED_MAX_COLS = 3;
    public static final int PERCEIVED_MIN_COLS = 2;
    public static final int COL_GAP = 4;
    public static final int FACE_FRAME_X = 1;
    public static final int FACE_FRAME_Y = 1;
    public static final int FACE_FRAME_SIZE = 18;
    public static final int FACE_X = 2;
    public static final int FACE_Y = 2;
    public static final int FACE_SIZE = 16;
    public static final int NAME_X = 23;
    public static final int NAME_Y = 2;
    public static final int ROLE_GEM_X = 23;
    public static final int ROLE_GEM_Y = 12;
    public static final int ROLE_X = 31;
    public static final int ROLE_Y = 11;
    public static final int CELL_PAD_R = 2;
    /** The ruled line under a row starts here and sits on the row's last pixel. 行下横线的起点；位于行的最后一像素。 */
    public static final int ROW_RULE_X = 22;

    // ---- Tab B: sectioned list ----
    public static final int HEADER_H = 14;
    public static final int ROW_H = 12;
    public static final int SECTION_GAP = 4;
    public static final int ABSENT_MAX_COLS = 4;
    public static final int ABSENT_MIN_COLS = 2;
    public static final int HEADER_MARK_Y = 4;
    public static final int HEADER_LABEL_X = 8;
    public static final int HEADER_LABEL_Y = 3;
    public static final int HEADER_RULE_Y = 7;
    public static final int HEADER_RULE_GAP = 4;
    /** A header rule shorter than this is not drawn. 短于此值的标题刻线不绘制。 */
    public static final int HEADER_RULE_MIN = 12;
    public static final int CELL_GEM_X = 4;
    public static final int CELL_GEM_Y = 3;
    public static final int CELL_TEXT_X = 12;
    public static final int CELL_TEXT_Y = 2;
    /** Trailing mark (current / visited / play), right-aligned in the cell. 行尾标记（当前/已领取/可选），在单元内右对齐。 */
    public static final int TRAIL_W = 5;
    public static final int TRAIL_RIGHT = 3;
    public static final int TRAIL_GAP = 4;
    public static final int TRAIL_Y = 3;
    public static final int SCROLL_STEP = 24;
    /** Reserved on the right of the list only while it scrolls. 仅在名单可滚动时于右侧预留。 */
    public static final int GUTTER = 5;
    public static final int ROD_W = 3;
    public static final int THUMB_MIN = 12;
    public static final int FADE_H = 8;

    // ---- footer (counted up from the panel bottom) ----
    public static final int FOOTER_RULE_Y = 22;
    public static final int FOOTER_ROW_Y = 17;
    public static final int FOOTER_X = 7;
    public static final int PILL_H = 11;
    public static final int PILL_PAD = 3;
    public static final int PILL_ICON_W = 5;
    public static final int PILL_ICON_GAP = 2;
    public static final int PILL_ICON_Y = 3;
    public static final int PILL_TEXT_Y = 2;
    public static final int GAUGE_GAP = 5;
    public static final int GAUGE_W = 44;
    public static final int GAUGE_MIN_W = 12;
    public static final int GAUGE_Y = 4;
    public static final int GAUGE_H = 4;
    /** Minimum air between the status and the right-hand element. 状态与右侧元素之间的最小间隔。 */
    public static final int FOOTER_GAP = 8;
    public static final int BUTTON_H = 13;
    public static final int BUTTON_Y = 18;
    public static final int BUTTON_PAD_L = 5;
    public static final int BUTTON_ICON_W = 7;
    public static final int BUTTON_ICON_GAP = 3;
    public static final int BUTTON_PAD_R = 6;
    public static final int BUTTON_ICON_Y = 2;
    public static final int BUTTON_TEXT_Y = 3;
    public static final int ARROW_SIZE = 11;
    public static final int PAGER_GAP = 4;

    // ---- empty state ----
    public static final int EMPTY_FEATHER_SCALE = 3;
    public static final int EMPTY_FEATHER_W = 7 * EMPTY_FEATHER_SCALE;
    public static final int EMPTY_FEATHER_H = 9 * EMPTY_FEATHER_SCALE;
    public static final int EMPTY_TEXT_GAP = 8;
    public static final int EMPTY_LINE_H = 11;

    // ---- tooltips ----
    public static final int TIP_PAD = 4;
    public static final int TIP_OFFSET = 12;
    public static final int TIP_LINE_H = 10;
    public static final int TIP_MAX_W = 200;

    private BlackRavenLedgerLayout() {
    }

    // ================================================================ panel

    /**
     * Centred panel: 304 x 206 on a 320 x 240 or larger scaled screen. Smaller scaled screens exist (Force Unicode
     * Font raises an odd GUI scale by one) and shrink it; the body then shows fewer rows.
     * 居中面板：缩放后不小于 320 × 240 的屏幕上为 304 × 206。更小的缩放屏幕确实存在（强制 Unicode 字体会把奇数界面
     * 缩放再加一），此时面板随之缩小，内容区显示的行数相应减少。
     */
    public static Panel panel(int screenWidth, int screenHeight) {
        int width = Math.max(0, Math.min(PANEL_MAX_W, screenWidth - 2 * SCREEN_MARGIN));
        int height = Math.max(0, Math.min(PANEL_MAX_H, screenHeight - 2 * SCREEN_MARGIN));
        return new Panel((screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
    }

    public record Panel(int x, int y, int width, int height) {
        public Rect frame() {
            return new Rect(x, y, width, height);
        }

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public int centerX() {
            return x + width / 2;
        }

        /** Inner x span shared by the band and the rules. 标题带与刻线共用的内侧 x 范围。 */
        public int innerLeft() {
            return x + BODY_INSET;
        }

        public int innerRight() {
            return right() - BODY_INSET;
        }

        /** The content viewport both tabs draw into; Tab B clips to it. 两个页签共用的内容视口；Tab B 按它裁剪。 */
        public Rect view() {
            int top = y + VIEW_TOP;
            return new Rect(x + VIEW_X, top, Math.max(0, width - 2 * VIEW_X),
                    Math.max(0, bottom() - FOOTER_H - top));
        }

        public int tabRuleY() {
            return y + TAB_RULE_Y;
        }

        public int footerRuleY() {
            return bottom() - FOOTER_RULE_Y;
        }

        public int footerRowY() {
            return bottom() - FOOTER_ROW_Y;
        }
    }

    // ================================================================ title band

    /**
     * Title and the disguise chip, right-aligned: label, role gem, role name, divider, Raven wallet. The chip yields
     * to the title in steps: first the label goes, then the role name shrinks (down to {@link #CHIP_ROLE_MIN_W}), then
     * the role goes and the wallet stays alone, then the wallet goes; the title is ellipsized only when even it does
     * not fit.
     * 标题与右对齐的伪装徽章：标签、职业宝石、职业名、分隔线、黑羽鸦钱包。徽章逐级让位于标题：先去掉标签，再压缩
     * 职业名（不窄于 {@link #CHIP_ROLE_MIN_W}），再去掉职业只留钱包，最后去掉钱包；只有标题自身放不下时才截断标题。
     *
     * @param labelWidth  chip label width / 徽章标签宽度
     * @param roleWidth   natural role name width, or 0 when not disguised / 职业名自然宽度；未伪装时为 0
     * @param walletWidth wallet text width (balance + coin), or 0 when hidden / 钱包文字宽度；隐藏时为 0
     */
    public static TitleBar titleBar(Panel panel, int titleWidth, int labelWidth, int roleWidth, int walletWidth) {
        int titleX = panel.x() + TITLE_X;
        int textY = panel.y() + TITLE_Y;
        int right = panel.right() - CHIP_RIGHT;
        int titleMax = Math.max(0, right - titleX);
        int left = titleX + Math.min(titleWidth, titleMax) + TITLE_CHIP_GAP;
        int walletX = -1;
        int dividerX = -1;
        int roleX = -1;
        int roleMax = 0;
        int gemX = -1;
        int labelX = -1;
        int end = right;
        if (walletWidth > 0 && right - walletWidth >= left) {
            walletX = right - walletWidth;
            end = walletX;
        }
        // The wallet outranks the role: a wallet that does not fit takes the whole chip with it, so the role never
        // comes back once the wallet is gone. 钱包优先于职业：钱包放不下时整个徽章一并隐藏，职业不会在钱包消失后重新出现。
        if (roleWidth > 0 && (walletWidth <= 0 || walletX >= 0)) {
            // With a wallet the role ends before the divider; without one it ends at the chip's right edge.
            int roleEnd = walletX >= 0 ? walletX - 2 * CHIP_GAP - 1 : right;
            int room = roleEnd - left - GEM_SIZE - GEM_TEXT_GAP;
            if (room >= Math.min(roleWidth, CHIP_ROLE_MIN_W)) {
                roleMax = Math.min(roleWidth, room);
                roleX = roleEnd - roleMax;
                gemX = roleX - GEM_TEXT_GAP - GEM_SIZE;
                if (walletX >= 0) {
                    dividerX = walletX - CHIP_GAP - 1;
                }
                end = gemX;
                if (labelWidth > 0 && roleMax == roleWidth && gemX - CHIP_GAP - labelWidth >= left) {
                    labelX = gemX - CHIP_GAP - labelWidth;
                    end = labelX;
                }
            }
        }
        int titleRoom = end == right ? titleMax : Math.max(0, end - TITLE_CHIP_GAP - titleX);
        return new TitleBar(titleX, textY, Math.min(titleMax, titleRoom), labelX, gemX, roleX, roleMax, dividerX,
                walletX);
    }

    /** Every x is -1 when its part is hidden. 各部分隐藏时对应的 x 为 -1。 */
    public record TitleBar(int titleX, int textY, int titleMaxWidth, int labelX, int gemX, int roleX,
                           int roleMaxWidth, int dividerX, int walletX) {
        public boolean labelShown() {
            return labelX >= 0;
        }

        public boolean roleShown() {
            return roleX >= 0;
        }

        public boolean walletShown() {
            return walletX >= 0;
        }

        public int gemY() {
            return textY + CHIP_GEM_Y;
        }

        /** Hover rect of the drawn wallet ({@code width} = its text width). 已绘制钱包的悬停区域。 */
        public Rect wallet(int width) {
            return walletShown() ? new Rect(walletX, textY - 1, width, 10) : Rect.EMPTY;
        }
    }

    // ================================================================ tabs

    /**
     * Tab rects from each tab's natural widths: {@code 2 * TAB_PAD + name + TAB_COUNT_GAP + count}. When the pair
     * overflows the row, each tab is capped to an even share and a short tab gives its spare share to the other. The
     * screen ellipsizes the name to {@link #tabNameMax}.
     * 按各页签的自然宽度计算矩形：{@code 2 * TAB_PAD + 名称 + TAB_COUNT_GAP + 数量}。两页签总宽超出一行时各自限制为
     * 平分宽度，较短的一方把多余份额让给另一方。界面将名称截断到 {@link #tabNameMax}。
     */
    public static List<Rect> tabs(Panel panel, int firstName, int firstCount, int secondName, int secondCount) {
        int available = Math.max(0, panel.width() - 2 * TABS_X - TAB_GAP);
        int first = 2 * TAB_PAD + firstName + TAB_COUNT_GAP + firstCount;
        int second = 2 * TAB_PAD + secondName + TAB_COUNT_GAP + secondCount;
        if (first + second > available) {
            int half = available / 2;
            if (first <= half) {
                second = available - first;
            } else if (second <= half) {
                first = available - second;
            } else {
                first = half;
                second = available - half;
            }
        }
        int x = panel.x() + TABS_X;
        int y = panel.y() + TAB_Y;
        return List.of(new Rect(x, y, first, TAB_H), new Rect(x + first + TAB_GAP, y, second, TAB_H));
    }

    public static int tabNameMax(Rect tab, int countWidth) {
        return Math.max(0, tab.width() - 2 * TAB_PAD - TAB_COUNT_GAP - countWidth);
    }

    /**
     * The "Tab" keycap and its hint at the right end of the tab row; hidden when it would crowd the tabs.
     * 页签行右端的 “Tab” 键帽与提示；会挤到页签时隐藏。
     */
    public static TabHint tabHint(Panel panel, List<Rect> tabs, int keyLabelWidth, int hintWidth) {
        int y = panel.y() + TAB_Y;
        int hintX = panel.right() - CHIP_RIGHT - hintWidth;
        int keyWidth = keyLabelWidth + 2 * KEYCAP_PAD;
        int keyX = hintX - HINT_GAP - keyWidth;
        int tabsRight = tabs.isEmpty() ? panel.x() + TABS_X : tabs.get(tabs.size() - 1).right();
        if (keyX < tabsRight + TAB_HINT_GAP) {
            return TabHint.HIDDEN;
        }
        return new TabHint(true, new Rect(keyX, y + KEYCAP_Y, keyWidth, KEYCAP_H), keyX + KEYCAP_PAD, hintX,
                y + TAB_TEXT_Y);
    }

    public record TabHint(boolean shown, Rect keycap, int keyTextX, int hintX, int textY) {
        public static final TabHint HIDDEN = new TabHint(false, Rect.EMPTY, 0, 0, 0);
    }

    // ================================================================ Tab A: perceived grid

    /** Columns and cell width of a ruled grid. 带横线网格的列数与单元宽度。 */
    public record Grid(int columns, int cellWidth) {
    }

    private static int cellWidth(int listWidth, int columns) {
        return Math.max(0, (listWidth - (columns - 1) * COL_GAP) / columns);
    }

    /**
     * Three columns while every role name fits its line, else two. Player names never widen the grid: they are
     * ellipsized and carry a tooltip.
     * 所有职业名都放得下时为三列，否则两列。玩家名不会撑宽网格：超宽时截断并提供提示框。
     *
     * @param widestRole the widest role name on the ledger / 感知册中最宽的职业名
     */
    public static Grid perceivedGrid(Panel panel, int widestRole) {
        int listWidth = panel.view().width();
        for (int columns = PERCEIVED_MAX_COLS; columns > PERCEIVED_MIN_COLS; columns--) {
            int width = cellWidth(listWidth, columns);
            if (perceivedRoleMax(width) >= widestRole) {
                return new Grid(columns, width);
            }
        }
        return new Grid(PERCEIVED_MIN_COLS, cellWidth(listWidth, PERCEIVED_MIN_COLS));
    }

    /**
     * Rows that fit the view: {@link #PERCEIVED_ROWS} on the full panel, fewer when a small scaled screen shrinks
     * it, never below one.
     * 视口能容纳的行数：完整面板为 {@link #PERCEIVED_ROWS} 行，小缩放屏幕使面板缩小时相应减少，至少一行。
     */
    public static int perceivedRows(Panel panel) {
        return Math.clamp((panel.view().height() - GRID_TOP_PAD) / PERCEIVED_PITCH, 1, PERCEIVED_ROWS);
    }

    public static int perceivedPerPage(Panel panel, Grid grid) {
        return grid.columns() * perceivedRows(panel);
    }

    public static int perceivedNameMax(int cellWidth) {
        return Math.max(0, cellWidth - NAME_X - CELL_PAD_R);
    }

    public static int perceivedRoleMax(int cellWidth) {
        return Math.max(0, cellWidth - ROLE_X - CELL_PAD_R);
    }

    /** Cell {@code index} on the page, row-major (left to right, then down). 页内第 index 个单元，按行优先排列。 */
    public static Rect perceivedCell(Panel panel, Grid grid, int index) {
        Rect view = panel.view();
        int column = Math.floorMod(index, grid.columns());
        int row = Math.floorDiv(index, grid.columns());
        return new Rect(view.x() + column * (grid.cellWidth() + COL_GAP),
                view.y() + GRID_TOP_PAD + row * PERCEIVED_PITCH, grid.cellWidth(), PERCEIVED_PITCH);
    }

    public static int pageCount(int entries, int perPage) {
        return Math.max(1, (entries + Math.max(1, perPage) - 1) / Math.max(1, perPage));
    }

    // ================================================================ Tab B: sectioned list

    /**
     * One section of the list in content coordinates (0 = the first header's top).
     * 名单中的一个分区，使用内容坐标（0 为首个标题的顶边）。
     */
    public record Block(int count, int headerY, int firstCell, int rows) {
        public int cellsTop() {
            return headerY + HEADER_H;
        }

        public int bottom() {
            return cellsTop() + rows * ROW_H;
        }
    }

    /** The laid-out list: {@code listWidth} excludes the scrollbar gutter. 排好的名单；listWidth 不含滚动条槽位。 */
    public record Content(Grid grid, int listWidth, List<Block> blocks, int height, boolean scrolls) {
    }

    /** Width a cell needs for its name; a trailing mark costs more than the plain right pad. 单元容纳名称所需的宽度。 */
    public static int cellNeed(int nameWidth, boolean trail) {
        return CELL_TEXT_X + nameWidth + (trail ? TRAIL_GAP + TRAIL_W + TRAIL_RIGHT : CELL_PAD_R);
    }

    public static int absentNameMax(int cellWidth, boolean trail) {
        return Math.max(0, cellWidth - cellNeed(0, trail));
    }

    /**
     * Lays the sections out as header + rows: the most columns (4 down to 2) whose cells hold {@code widestNeed}.
     * When the result is taller than the view, it is laid out again without the scrollbar gutter.
     * 将各分区排成 标题 + 若干行：在 4 至 2 列中取单元仍能容纳 widestNeed 的最大列数。若结果高于视口，则扣除滚动条槽位后
     * 重新排布。
     *
     * @param sectionCounts cells per section, in display order; empty sections are skipped / 各分区的单元数；空分区跳过
     * @param widestNeed    the largest {@link #cellNeed} of any cell / 所有单元中最大的 cellNeed
     */
    public static Content absentContent(Panel panel, List<Integer> sectionCounts, int widestNeed) {
        Rect view = panel.view();
        Content full = content(view.width(), sectionCounts, widestNeed, false);
        if (full.height() <= view.height()) {
            return full;
        }
        return content(Math.max(0, view.width() - GUTTER), sectionCounts, widestNeed, true);
    }

    private static Content content(int listWidth, List<Integer> sectionCounts, int widestNeed, boolean scrolls) {
        int columns = ABSENT_MIN_COLS;
        for (int candidate = ABSENT_MAX_COLS; candidate > ABSENT_MIN_COLS; candidate--) {
            if (cellWidth(listWidth, candidate) >= widestNeed) {
                columns = candidate;
                break;
            }
        }
        Grid grid = new Grid(columns, cellWidth(listWidth, columns));
        List<Block> blocks = new ArrayList<>();
        int y = 0;
        int cells = 0;
        for (int count : sectionCounts) {
            if (count <= 0) {
                continue;
            }
            if (!blocks.isEmpty()) {
                y += SECTION_GAP;
            }
            int rows = (count + columns - 1) / columns;
            Block block = new Block(count, y, cells, rows);
            blocks.add(block);
            y = block.bottom();
            cells += count;
        }
        return new Content(grid, listWidth, List.copyOf(blocks), y, scrolls);
    }

    /** Screen rect of a section header at the given scroll. 给定滚动位置下分区标题的屏幕矩形。 */
    public static Rect header(Panel panel, Content content, Block block, int scroll) {
        Rect view = panel.view();
        return new Rect(view.x(), view.y() + block.headerY() - scroll, content.listWidth(), HEADER_H);
    }

    /**
     * The engraved rule between a header's label and its count tail; not shown when shorter than
     * {@link #HEADER_RULE_MIN}. 标题文字与数量之间的刻线；短于 HEADER_RULE_MIN 时不显示。
     */
    public static Rule headerRule(Rect header, int labelWidth, int tailWidth) {
        int start = header.x() + HEADER_LABEL_X + labelWidth + HEADER_RULE_GAP;
        int end = header.right() - tailWidth - (tailWidth > 0 ? HEADER_RULE_GAP : 0);
        return new Rule(end - start >= HEADER_RULE_MIN, start, end, header.y() + HEADER_RULE_Y);
    }

    public record Rule(boolean shown, int x1, int x2, int y) {
    }

    /** Screen rect of list cell {@code cell} (section-major, then row-major). 名单第 cell 个单元的屏幕矩形。 */
    public static Rect absentCell(Panel panel, Content content, int cell, int scroll) {
        Rect view = panel.view();
        Grid grid = content.grid();
        for (Block block : content.blocks()) {
            if (cell >= block.firstCell() && cell < block.firstCell() + block.count()) {
                int local = cell - block.firstCell();
                return new Rect(view.x() + (local % grid.columns()) * (grid.cellWidth() + COL_GAP),
                        view.y() + block.cellsTop() + (local / grid.columns()) * ROW_H - scroll, grid.cellWidth(),
                        ROW_H);
            }
        }
        return Rect.EMPTY;
    }

    /** Trailing mark x inside a cell. 单元内行尾标记的 x。 */
    public static int trailX(Rect cell) {
        return cell.right() - TRAIL_RIGHT - TRAIL_W;
    }

    /** The list cell under the pointer, or -1; only inside the view. 指针下的名单单元；不在视口内时为 -1。 */
    public static int absentCellAt(Panel panel, Content content, int scroll, double mouseX, double mouseY) {
        Rect view = panel.view();
        if (!view.contains(mouseX, mouseY) || mouseX >= view.x() + content.listWidth()) {
            return -1;
        }
        Grid grid = content.grid();
        int pitch = grid.cellWidth() + COL_GAP;
        int localX = (int) Math.floor(mouseX) - view.x();
        int column = localX / pitch;
        if (column >= grid.columns() || localX - column * pitch >= grid.cellWidth()) {
            return -1;
        }
        int y = (int) Math.floor(mouseY) - view.y() + scroll;
        for (Block block : content.blocks()) {
            if (y >= block.cellsTop() && y < block.bottom()) {
                int index = (y - block.cellsTop()) / ROW_H * grid.columns() + column;
                return index < block.count() ? block.firstCell() + index : -1;
            }
        }
        return -1;
    }

    // ---- scrolling ----

    public static int maxScroll(Panel panel, Content content) {
        return Math.max(0, content.height() - panel.view().height());
    }

    public static int clampScroll(Panel panel, Content content, int scroll) {
        return Math.max(0, Math.min(maxScroll(panel, content), scroll));
    }

    /** {@link #SCROLL_STEP} px per wheel notch. 滚轮每格滚动 SCROLL_STEP 像素。 */
    public static int wheel(Panel panel, Content content, int scroll, double verticalAmount) {
        return clampScroll(panel, content, scroll - (int) Math.round(verticalAmount * SCROLL_STEP));
    }

    /** PageUp (-1) / PageDown (+1): one viewport per page. 翻页键每次滚动一个视口高度。 */
    public static int page(Panel panel, Content content, int scroll, int pages) {
        return clampScroll(panel, content, scroll + pages * panel.view().height());
    }

    /** The scrollbar track: the gutter's right-hand ROD_W columns over the full view height. 滚动条轨道。 */
    public static Rect track(Panel panel) {
        Rect view = panel.view();
        return new Rect(view.right() - ROD_W, view.y(), ROD_W, view.height());
    }

    /** The rod on its track; {@link Rect#EMPTY} when the list does not scroll. 滑杆；名单不可滚动时为空矩形。 */
    public static Rect thumb(Panel panel, Content content, int scroll) {
        int max = maxScroll(panel, content);
        if (max <= 0) {
            return Rect.EMPTY;
        }
        Rect track = track(panel);
        int size = thumbSize(track.height(), content.height());
        int offset = clampScroll(panel, content, scroll) * (track.height() - size) / max;
        return new Rect(track.x(), track.y() + offset, ROD_W, size);
    }

    /** Drag or click-to-jump in the gutter: the rod centre follows the pointer. 拖动或点击槽位：滑杆中心跟随指针。 */
    public static int dragScroll(Panel panel, Content content, double mouseY) {
        int max = maxScroll(panel, content);
        if (max <= 0) {
            return 0;
        }
        Rect track = track(panel);
        int size = thumbSize(track.height(), content.height());
        double fraction = (mouseY - track.y() - size / 2.0) / Math.max(1, track.height() - size);
        return (int) Math.round(Math.max(0.0, Math.min(1.0, fraction)) * max);
    }

    private static int thumbSize(int track, int total) {
        return Math.min(track, Math.max(THUMB_MIN, track * track / Math.max(1, total)));
    }

    // ================================================================ footer

    /**
     * Footer status: a pill (icon + text) and, for countdowns, a gauge after it. The pill is ellipsized to the room
     * left of {@code rightWidth} (the revert button or the pager, 0 when neither shows); the gauge shrinks and is
     * dropped below {@link #GAUGE_MIN_W}.
     * 页脚状态：状态牌（图标 + 文字），倒计时状态在其后带计量条。状态牌截断到右侧元素（恢复按钮或翻页，均无时为 0）之前
     * 的空间；计量条随之缩短，窄于 {@link #GAUGE_MIN_W} 时省略。
     *
     * @param statusTextWidth natural status text width, or 0 when there is no status / 状态文字自然宽度；无状态时为 0
     */
    public static Footer footer(Panel panel, int statusTextWidth, boolean gauge, int rightWidth) {
        int y = panel.footerRowY();
        int x = panel.x() + FOOTER_X;
        int limit = panel.right() - FOOTER_X - (rightWidth > 0 ? rightWidth + FOOTER_GAP : 0);
        if (statusTextWidth <= 0) {
            return new Footer(Rect.EMPTY, 0, Rect.EMPTY);
        }
        int chrome = PILL_PAD + PILL_ICON_W + PILL_ICON_GAP + PILL_PAD;
        int textMax = Math.max(0, Math.min(statusTextWidth, limit - x - chrome));
        Rect pill = new Rect(x, y, chrome + textMax, PILL_H);
        Rect bar = Rect.EMPTY;
        if (gauge) {
            int barX = pill.right() + GAUGE_GAP;
            int barWidth = Math.min(GAUGE_W, limit - barX);
            if (barWidth >= GAUGE_MIN_W) {
                bar = new Rect(barX, y + GAUGE_Y, barWidth, GAUGE_H);
            }
        }
        return new Footer(pill, textMax, bar);
    }

    /** {@code pill} is {@link Rect#EMPTY} when there is no status, {@code gauge} when it is hidden. 无状态时 pill 为空矩形。 */
    public record Footer(Rect pill, int textMaxWidth, Rect gauge) {
        public int iconX() {
            return pill.x() + PILL_PAD;
        }

        public int iconY() {
            return pill.y() + PILL_ICON_Y;
        }

        public int textX() {
            return iconX() + PILL_ICON_W + PILL_ICON_GAP;
        }

        public int textY() {
            return pill.y() + PILL_TEXT_Y;
        }
    }

    /** Depleting gauge fill: {@code remaining / total} of {@code width}, clamped. 递减计量条的填充宽度。 */
    public static int gaugeFill(int width, int remaining, int total) {
        if (width <= 0 || total <= 0 || remaining <= 0) {
            return 0;
        }
        return (int) Math.round(width * Math.min(1.0, remaining / (double) total));
    }

    public static int buttonWidth(int labelWidth) {
        return BUTTON_PAD_L + BUTTON_ICON_W + BUTTON_ICON_GAP + labelWidth + BUTTON_PAD_R;
    }

    /** The revert button, right-aligned in the footer. 页脚右对齐的恢复按钮。 */
    public static Rect button(Panel panel, int labelWidth) {
        int width = buttonWidth(labelWidth);
        return new Rect(panel.right() - FOOTER_X - width, panel.bottom() - BUTTON_Y, width, BUTTON_H);
    }

    /**
     * Pager, right-aligned in the footer: "◀" box, "n / m", "▶" box. Hidden when there is a single page.
     * 页脚右侧翻页：“◀”框、“n / m”、“▶”框；只有一页时隐藏。
     */
    public static Pager pager(Panel panel, int pageTextWidth, int pageCount) {
        if (pageCount <= 1) {
            return Pager.HIDDEN;
        }
        int y = panel.footerRowY();
        int nextX = panel.right() - FOOTER_X - ARROW_SIZE;
        int textX = nextX - PAGER_GAP - pageTextWidth;
        int prevX = textX - PAGER_GAP - ARROW_SIZE;
        return new Pager(true, new Rect(prevX, y, ARROW_SIZE, ARROW_SIZE), new Rect(nextX, y, ARROW_SIZE, ARROW_SIZE),
                textX, y + PILL_TEXT_Y);
    }

    public record Pager(boolean shown, Rect prev, Rect next, int textX, int textY) {
        public static final Pager HIDDEN = new Pager(false, Rect.EMPTY, Rect.EMPTY, 0, 0);

        public int width() {
            return shown ? next.right() - prev.x() : 0;
        }
    }

    // ================================================================ empty state

    /**
     * Top of the centred empty block (large feather, message, up to {@code textLines} more lines) in the view.
     * 视口内居中的空状态块（大羽毛、提示文字及其后最多 textLines 行）的顶边。
     */
    public static int emptyTop(Panel panel, int textLines) {
        Rect view = panel.view();
        int blockHeight = EMPTY_FEATHER_H + EMPTY_TEXT_GAP + Math.max(1, textLines) * EMPTY_LINE_H;
        return view.y() + Math.max(0, (view.height() - blockHeight) / 2);
    }

    // ================================================================ tooltips

    /**
     * Tooltip content box near the mouse, flipped and clamped so the box (with TIP_PAD) stays on screen.
     * 鼠标附近的提示框内容区，必要时翻转并夹紧，使提示框（含 TIP_PAD）留在屏幕内。
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

    // ================================================================ hit test

    public enum HitKind {
        NONE,
        TAB,
        CELL,
        PREV,
        NEXT,
        REVERT,
        ROD
    }

    /** {@code index} = tab index, or the page-local (Tab A) / list (Tab B) cell index; 0 otherwise. */
    public record Hit(HitKind kind, int index) {
        public static final Hit NONE = new Hit(HitKind.NONE, 0);
    }

    private static Hit hitTab(List<Rect> tabs, double mouseX, double mouseY) {
        for (int index = 0; index < tabs.size(); index++) {
            if (tabs.get(index).contains(mouseX, mouseY)) {
                return new Hit(HitKind.TAB, index);
            }
        }
        return Hit.NONE;
    }

    /**
     * Tab A hit test against the rects as painted. Pager arrows hit only while shown and enabled; cells only up to
     * {@code cellCount} (the entries actually on this page); outside the frame nothing hits.
     * Tab A 按绘制矩形进行命中判定。翻页箭头仅在显示且可用时命中；单元只判定本页实际存在的 cellCount 个；面板外不命中。
     */
    public static Hit hitPerceived(Panel panel, List<Rect> tabs, Pager pager, int page, int pageCount, Grid grid,
                                   int cellCount, double mouseX, double mouseY) {
        if (!panel.frame().contains(mouseX, mouseY)) {
            return Hit.NONE;
        }
        Hit tab = hitTab(tabs, mouseX, mouseY);
        if (tab.kind() != HitKind.NONE) {
            return tab;
        }
        if (pager.shown()) {
            if (page > 0 && pager.prev().contains(mouseX, mouseY)) {
                return new Hit(HitKind.PREV, 0);
            }
            if (page < pageCount - 1 && pager.next().contains(mouseX, mouseY)) {
                return new Hit(HitKind.NEXT, 0);
            }
        }
        int cells = Math.min(cellCount, perceivedPerPage(panel, grid));
        for (int index = 0; index < cells; index++) {
            if (perceivedCell(panel, grid, index).contains(mouseX, mouseY)) {
                return new Hit(HitKind.CELL, index);
            }
        }
        return Hit.NONE;
    }

    /**
     * Tab B hit test against the rects as painted. The revert button hits only while shown and enabled
     * ({@code button} is {@link Rect#EMPTY} otherwise); the gutter hits only while the list scrolls; cells only inside
     * the view.
     * Tab B 按绘制矩形进行命中判定。恢复按钮仅在显示且可用时命中（否则传入空矩形）；槽位仅在名单可滚动时命中；
     * 单元仅在视口内命中。
     */
    public static Hit hitAbsent(Panel panel, List<Rect> tabs, Content content, int scroll, Rect button,
                                double mouseX, double mouseY) {
        if (!panel.frame().contains(mouseX, mouseY)) {
            return Hit.NONE;
        }
        Hit tab = hitTab(tabs, mouseX, mouseY);
        if (tab.kind() != HitKind.NONE) {
            return tab;
        }
        if (button.contains(mouseX, mouseY)) {
            return new Hit(HitKind.REVERT, 0);
        }
        Rect view = panel.view();
        if (content.scrolls() && view.contains(mouseX, mouseY) && mouseX >= view.right() - GUTTER) {
            return new Hit(HitKind.ROD, 0);
        }
        int cell = absentCellAt(panel, content, scroll, mouseX, mouseY);
        return cell >= 0 ? new Hit(HitKind.CELL, cell) : Hit.NONE;
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

        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }

        public boolean intersects(Rect other) {
            return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
        }
    }
}
