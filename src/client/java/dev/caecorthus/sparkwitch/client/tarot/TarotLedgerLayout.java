package dev.caecorthus.sparkwitch.client.tarot;

import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerEntries.Section;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * Pure layout of the Tarot Reader divination selector: every size, sub-rect, hit test and scroll rule, so what is
 * clicked is exactly what is painted. {@code TarotLedgerPaint} draws with these sizes and never declares its own. It
 * holds no client types; the screen measures text and passes the widths in. GUI px; right and bottom edges are
 * exclusive, like {@code DrawContext.fill}. Content y values are offsets from the list top at scroll 0.
 * 塔罗牌师占卜选择界面的纯布局：所有尺寸、子区域、命中判定与滚动规则都在此处，保证点击区域与绘制位置一致；
 * {@code TarotLedgerPaint} 使用这些尺寸绘制，不另行声明。不含客户端类型，文本宽度由界面测量后传入。单位为 GUI 像素，
 * 右缘与下缘为开区间（与 fill 一致）；内容 y 为滚动为 0 时相对列表顶边的偏移。
 */
public final class TarotLedgerLayout {
    // ---- panel ----
    public static final int SCREEN_MARGIN = 6;
    public static final int IDENTITY_MAX_W = 312;
    public static final int SURVIVAL_MAX_W = 240;
    public static final int PANEL_MAX_H = 232;
    public static final int PANEL_MIN_H = 118;
    /** {@code InventoryCardPaint.panel} drops a 1 px shadow right of and under the frame. 面板右侧与下方的 1 像素投影。 */
    public static final int PANEL_SHADOW = 1;
    /** Scrim air kept between the panel's shadow and a redrawn HUD backing. 面板投影与重绘 HUD 底板之间保留的空隙。 */
    public static final int HUD_AIR = 2;

    // ---- title band ----
    public static final int TITLE_ICON_X = 7;
    public static final int TITLE_ICON_Y = 6;
    public static final int TITLE_X = 18;
    public static final int TITLE_Y = 7;
    public static final int CHIP_RIGHT = 7;
    public static final int CHIP_GAP = 4;
    public static final int COIN_GAP = 1;
    /** Advance of Wathe's coin glyph U+E781. Wathe 金币字形 U+E781 的前进宽度。 */
    public static final int COIN_ADVANCE = 9;
    public static final int TITLE_CHIP_GAP = 8;

    // ---- search well and jump tabs ----
    public static final int WELL_X = 6;
    public static final int WELL_Y = 24;
    public static final int WELL_H = 14;
    public static final int WELL_PAD_R = 4;
    public static final int MAGNIFIER_INSET = 3;
    public static final int FIELD_X = 21;
    public static final int FIELD_Y = 27;
    /** The placeholder moves right of vanilla's "_" caret while focused. 聚焦时占位文字右移，避开原版 “_” 光标。 */
    public static final int PLACEHOLDER_FOCUS_SHIFT = 7;
    public static final int CLEAR_SIZE = 5;
    public static final int CLEAR_Y = 4;
    public static final int CLEAR_HIT_PAD = 3;
    public static final int COUNT_GAP = 5;
    public static final int FIELD_COUNT_GAP = 4;
    public static final int TAB_W = 11;
    public static final int TAB_H = 14;
    public static final int TAB_GAP = 1;
    public static final int TABS_GAP = 3;
    public static final int TAB_MARK_X = 3;
    public static final int TAB_MARK_Y = 4;
    /** Identity jump tabs, left to right, in the HUD's faction order. 身份模式跳转标签，按 HUD 阵营顺序从左到右。 */
    public static final List<Section> TAB_SECTIONS =
            List.of(Section.CIVILIAN, Section.KILLER, Section.NEUTRAL, Section.WITCH, Section.SPECIAL);
    private static final int TABS_W = TAB_SECTIONS.size() * TAB_W + (TAB_SECTIONS.size() - 1) * TAB_GAP;

    // ---- list ----
    public static final int VIEW_TOP = 42;
    public static final int VIEW_BOTTOM_PAD = 2;
    public static final int VIEW_INSET = 3;
    public static final int ROW_LEFT = 4;
    public static final int ROW_RIGHT = 4;
    /**
     * Always reserved, so the grid never reflows when the list starts to scroll. The rod is its left 3 px.
     * 滚动条槽位始终保留，列表开始滚动时网格不会重排。滑杆占其左侧 3 像素。
     */
    public static final int GUTTER = 5;
    public static final int ROD_W = 3;
    public static final int COL_GAP = 4;
    public static final int ROW_H = 12;
    public static final int HEADER_H = 14;
    /** Air above every section header but the first. 除第一个外，每个分区标题上方的间隔。 */
    public static final int SECTION_GAP = 4;
    public static final int HEADER_MARK_Y = 4;
    /** The {@code sectionHeader} y; it draws its label 1 px lower. {@code sectionHeader} 的 y，标签再下移 1 像素。 */
    public static final int HEADER_LABEL_Y = 2;
    public static final int HEADER_RIGHT_PAD = 2;
    public static final int CELL_GEM_X = 3;
    public static final int CELL_GEM_Y = 3;
    public static final int CELL_TEXT_X = 11;
    public static final int CELL_TEXT_Y = 2;
    public static final int CELL_PAD_R = 2;
    /** Name to trailing mark (self tag, then stamp). 名称与尾随标记（自身标签、印章）之间的间隔。 */
    public static final int TRAIL_GAP = 4;
    public static final int STAMP_W = 5;
    public static final int STAMP_Y = 3;
    public static final int NAME_CAP_IDENTITY = 82;
    public static final int NAME_CAP_PLAYER = 100;
    public static final int MAX_COLS_IDENTITY = 4;
    public static final int MAX_COLS_SURVIVAL = 2;
    public static final int SCROLL_STEP = 24;
    public static final int FADE_H = 8;
    public static final int THUMB_MIN = 12;
    /** Empty state, from the viewport top, centred on {@link Panel#centerX()}. 空结果状态，自视口顶边起算并居中。 */
    public static final int EMPTY_MARK_Y = 12;
    public static final int EMPTY_TEXT_Y = 26;
    public static final int EMPTY_HINT_Y = 38;

    // ---- footer (y offsets count up from the panel bottom) ----
    public static final int FOOTER_H = 22;
    public static final int FOOTER_RULE_Y = 21;
    /** Top of the 13 px footer row, bottom-17..bottom-4. 13 像素页脚行的顶边。 */
    public static final int FOOTER_ROW_Y = 17;
    public static final int FOOTER_INSET = 7;
    /**
     * Every footer label (keycap, forfeit note, question or hint, button) sits this far below the row top.
     * 页脚所有文字（按键帽、放弃说明、问句或提示、按钮）距行顶的偏移。
     */
    public static final int FOOTER_TEXT_Y = 3;
    public static final int FORFEIT_GAP = 4;
    public static final int MESSAGE_GAP = 8;
    public static final int BUTTON_GAP = 6;
    /** A 5 px gem or bullet plus 3 px before the footer question. 页脚问句前的 5 像素宝石或圆点加 3 像素间隔。 */
    public static final int MARK_ADVANCE = 8;
    public static final int FOOTER_MARK_Y = 4;
    /** The Esc keycap sits 1 px below the row top. Esc 按键帽比行顶低 1 像素。 */
    public static final int KEYCAP_Y = 1;
    public static final int KEYCAP_H = 11;
    public static final int KEYCAP_LABEL_X = 3;
    public static final int KEYCAP_LABEL_Y = FOOTER_TEXT_Y - KEYCAP_Y;
    /** The Divine button fills the 13 px row: 6 + play icon 3 + 3 + label + 6. “占卜”按钮占满 13 像素页脚行。 */
    public static final int BUTTON_H = 13;
    public static final int BUTTON_ICON_X = 6;
    public static final int BUTTON_ICON_Y = 4;
    public static final int BUTTON_LABEL_X = 12;
    public static final int BUTTON_LABEL_Y = FOOTER_TEXT_Y;
    public static final int BUTTON_PAD_R = 6;

    // ---- tooltips ----
    /** Content top = anchor bottom + 5, so the box edge sits 1 px under the anchor. 提示框边缘位于锚点下方 1 像素。 */
    public static final int TIP_DROP = 5;
    /** {@code brassTooltip} reaches 4 px outside its content box. 黄铜提示框超出内容区 4 像素。 */
    public static final int TIP_PAD = 4;
    private static final int TIP_LINE_H = 8;
    private static final int TIP_FIRST_GAP = 2;
    private static final int TIP_LINE_PITCH = 10;

    private TarotLedgerLayout() {
    }

    // ================================================================ grid and panel

    public static int panelWidth(int screenWidth, Mode mode) {
        return Math.max(0, Math.min(mode.maxWidth, screenWidth - 2 * SCREEN_MARGIN));
    }

    public static int listWidth(int panelWidth) {
        return Math.max(0, panelWidth - ROW_LEFT - ROW_RIGHT - GUTTER);
    }

    /**
     * Column rule: {@code need = 11 + min(widest, cap) + 2}, {@code cols = clamp((listW + 4) / (need + 4), 1, max)}.
     * 列数规则：按最宽名称（受上限约束）计算单元所需宽度，再求可容纳的列数。
     */
    public static int columns(int listWidth, int widestName, Mode mode) {
        int need = CELL_TEXT_X + Math.min(Math.max(0, widestName), mode.nameCap) + CELL_PAD_R;
        return Math.max(1, Math.min(mode.maxColumns, (listWidth + COL_GAP) / (need + COL_GAP)));
    }

    public static int cellWidth(int listWidth, int columns) {
        return Math.max(0, (listWidth - (columns - 1) * COL_GAP) / columns);
    }

    /**
     * The grid for this screen width. {@code widestName} must be measured over the UNFILTERED list once per
     * {@code init()}, so typing a search never reflows the columns. The panel width depends only on the screen
     * width and mode (the HUD nudge moves the panel, never resizes it).
     * 当前屏幕宽度下的网格。{@code widestName} 必须在每次 {@code init()} 时对未筛选的完整列表测量一次，输入搜索时列数
     * 不会变化。面板宽度只取决于屏幕宽度与模式（避让 HUD 只移动面板，不改变尺寸）。
     */
    public static Grid grid(int screenWidth, Mode mode, int widestName) {
        int listWidth = listWidth(panelWidth(screenWidth, mode));
        int columns = columns(listWidth, widestName, mode);
        return new Grid(columns, cellWidth(listWidth, columns));
    }

    /**
     * Centred panel. {@code contentHeight} is the UNFILTERED content height, so the frame never jumps while typing.
     * If the frame (with its shadow and {@link #HUD_AIR}) overlaps {@code hudBacking}, the panel shifts left just
     * enough to clear it while {@code x >= SCREEN_MARGIN}; otherwise it stays centred and the HUD is not redrawn.
     * 居中面板。{@code contentHeight} 为未筛选内容的高度，输入搜索时外框不会跳动。若外框（含投影与
     * {@link #HUD_AIR}）与 {@code hudBacking} 重叠，在 {@code x >= SCREEN_MARGIN} 的前提下将面板左移至恰好避开；
     * 否则保持居中且不重绘 HUD。
     *
     * @param hudBacking the HUD table backing from {@code TarotDivinationHudRenderer.backingBounds}, or null when
     *                   no table is shown / HUD 表格底板，无表格时为 null
     */
    public static Panel panel(int screenWidth, int screenHeight, Mode mode, int contentHeight,
                              @Nullable Rect hudBacking) {
        int width = panelWidth(screenWidth, mode);
        int maxHeight = Math.max(0, Math.min(PANEL_MAX_H, screenHeight - 2 * SCREEN_MARGIN));
        int need = VIEW_TOP + Math.max(0, contentHeight) + VIEW_BOTTOM_PAD + FOOTER_H;
        int height = Math.max(Math.min(PANEL_MIN_H, maxHeight), Math.min(maxHeight, need));
        Rect frame = new Rect((screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
        if (hudBacking == null || hudBacking.width() <= 0 || hudBacking.height() <= 0) {
            return new Panel(mode, frame);
        }
        if (clearOfHud(frame, hudBacking)) {
            return new Panel(mode, frame);
        }
        int nudged = hudBacking.x() - HUD_AIR - PANEL_SHADOW - width;
        Rect moved = new Rect(nudged, frame.y(), width, height);
        if (nudged >= SCREEN_MARGIN && nudged < frame.x() && clearOfHud(moved, hudBacking)) {
            return new Panel(mode, moved);
        }
        return new Panel(mode, frame);
    }

    /**
     * Whether the panel frame, its 1 px shadow and {@link #HUD_AIR} of air all stay clear of the HUD table's backing.
     * 面板外框、1 像素投影及 {@link #HUD_AIR} 空隙是否都避开了 HUD 表格底板。
     */
    public static boolean clearOfHud(Rect frame, Rect hud) {
        Rect footprint = new Rect(frame.x() - HUD_AIR, frame.y() - HUD_AIR,
                frame.width() + PANEL_SHADOW + 2 * HUD_AIR, frame.height() + PANEL_SHADOW + 2 * HUD_AIR);
        return !footprint.intersects(hud);
    }

    /** Title, icon and paid chip ("已付 50" + coin) in the band. 标题带中的标题、图标与已付金额。 */
    public static TitleBar titleBar(Panel panel, int paidLabelWidth, int priceWidth) {
        Rect f = panel.frame();
        int chipWidth = paidLabelWidth + CHIP_GAP + priceWidth + COIN_GAP + COIN_ADVANCE;
        int paidX = f.right() - CHIP_RIGHT - chipWidth;
        int titleX = f.x() + TITLE_X;
        int priceX = paidX + paidLabelWidth + CHIP_GAP;
        return new TitleBar(f.x() + TITLE_ICON_X, f.y() + TITLE_ICON_Y, titleX, f.y() + TITLE_Y,
                Math.max(0, paidX - TITLE_CHIP_GAP - titleX), paidX, priceX, priceX + priceWidth + COIN_GAP);
    }

    // ================================================================ content

    /**
     * Content model, cells filled row-major inside each section. Sections with no entries are skipped, so pass the
     * filtered groups as they are; cell indices run across sections in order.
     * 内容模型，各分区内按行优先填充。无条目的分区会被跳过；单元索引按分区顺序连续编号。
     */
    public static Content content(List<SectionSize> sections, Grid grid) {
        List<Block> blocks = new ArrayList<>(sections.size());
        int y = 0;
        int cell = 0;
        for (SectionSize size : sections) {
            if (size.count() <= 0) {
                continue;
            }
            if (!blocks.isEmpty()) {
                y += SECTION_GAP;
            }
            int rows = (size.count() + grid.columns() - 1) / grid.columns();
            blocks.add(new Block(size.section(), size.count(), y, cell, rows));
            y += HEADER_H + rows * ROW_H;
            cell += size.count();
        }
        return new Content(grid, blocks, y, cell);
    }

    public static Rect cell(Panel panel, Content content, int cell, int scroll) {
        Grid grid = content.grid();
        int x = panel.listLeft() + content.column(cell) * (grid.cellWidth() + COL_GAP);
        return new Rect(x, panel.viewTop() - scroll + content.cellY(cell), grid.cellWidth(), ROW_H);
    }

    public static int headerY(Panel panel, Block block, int scroll) {
        return panel.viewTop() - scroll + block.headerY();
    }

    /**
     * Width left for a cell name after its trailing marks; the name is ellipsized to it.
     * 扣除尾随标记后名称可用的宽度，名称按此省略。
     *
     * @param tagWidth the self tag width, or 0 when the row has none / 自身标签宽度，无标签时为 0
     */
    public static int nameMaxWidth(int cellWidth, int tagWidth, boolean stamped) {
        int reserve = CELL_PAD_R + (tagWidth > 0 ? TRAIL_GAP + tagWidth : 0) + (stamped ? TRAIL_GAP + STAMP_W : 0);
        return Math.max(0, cellWidth - CELL_TEXT_X - reserve);
    }

    // ================================================================ hit testing

    /**
     * What the pointer is over. The gutter is tested before cells and cells end 1 px before it, so a scrollbar
     * click can never spend the reading. Tabs of sections with no matches, rows under the running head, the
     * section gaps and the column gaps hit nothing.
     * 指针所在的元素。滚动条槽位先于单元判定，且单元在其左侧 1 像素处结束，点击滚动条绝不会花掉本次占卜。无匹配分区的
     * 标签、被置顶标题遮住的行、分区间隔与列间隔均不命中。
     *
     * @param clearShown whether the clear × is drawn (the query is not empty) / 是否显示清除按钮（查询非空）
     */
    public static Hit hit(Panel panel, Content content, Footer footer, int scroll, boolean clearShown,
                          double mouseX, double mouseY) {
        if (footer.button().contains(mouseX, mouseY)) {
            return new Hit(HitKind.BUTTON, -1, null);
        }
        if (clearShown && panel.clearBox().contains(mouseX, mouseY)) {
            return new Hit(HitKind.CLEAR, -1, null);
        }
        List<Rect> tabs = panel.tabs();
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).contains(mouseX, mouseY)) {
                Section section = TAB_SECTIONS.get(i);
                return content.block(section) != null ? new Hit(HitKind.TAB, -1, section) : Hit.NONE;
            }
        }
        if (!panel.viewport().contains(mouseX, mouseY)) {
            return Hit.NONE;
        }
        if (panel.gutter().contains(mouseX, mouseY)) {
            return new Hit(HitKind.GUTTER, -1, null);
        }
        int cell = cellAt(panel, content, scroll, mouseX, mouseY);
        return cell < 0 ? Hit.NONE : new Hit(HitKind.CELL, cell, content.blockOf(cell).section());
    }

    private static int cellAt(Panel panel, Content content, int scroll, double mouseX, double mouseY) {
        if (mouseX < panel.listLeft() || mouseX >= panel.listRight()) {
            return -1;
        }
        Optional<RunningHead> head = runningHead(panel, content, scroll);
        if (head.isPresent() && mouseY < head.get().bottom()) {
            return -1;
        }
        Grid grid = content.grid();
        int pitch = grid.cellWidth() + COL_GAP;
        int localX = (int) Math.floor(mouseX) - panel.listLeft();
        int column = localX / pitch;
        if (column >= grid.columns() || localX - column * pitch >= grid.cellWidth()) {
            return -1;
        }
        int y = (int) Math.floor(mouseY) - panel.viewTop() + scroll;
        for (Block block : content.blocks()) {
            if (y >= block.cellsTop() && y < block.bottom()) {
                int index = (y - block.cellsTop()) / ROW_H * grid.columns() + column;
                return index < block.count() ? block.firstCell() + index : -1;
            }
        }
        return -1;
    }

    // ================================================================ scrolling

    public static int maxScroll(Panel panel, Content content) {
        return Math.max(0, content.height() - panel.viewHeight());
    }

    public static int clampScroll(Panel panel, Content content, int scroll) {
        return Math.max(0, Math.min(maxScroll(panel, content), scroll));
    }

    /** {@link #SCROLL_STEP} px per notch; from 0 it lands rows flush under the running head. 每格滚动 24 像素。 */
    public static int wheel(Panel panel, Content content, int scroll, double verticalAmount) {
        return clampScroll(panel, content, scroll - (int) Math.round(verticalAmount * SCROLL_STEP));
    }

    /** PageUp (-1) / PageDown (+1): one viewport per page. 翻页键每次滚动一个视口高度。 */
    public static int page(Panel panel, Content content, int scroll, int pages) {
        return clampScroll(panel, content, scroll + pages * panel.viewHeight());
    }

    /**
     * The rod, spanning the viewport height as its track; empty when the list does not scroll.
     * 滑杆，以视口高度为轨道；列表不可滚动时为空。
     */
    public static Optional<Rect> thumb(Panel panel, Content content, int scroll) {
        int max = maxScroll(panel, content);
        if (max <= 0) {
            return Optional.empty();
        }
        int track = panel.viewHeight();
        int size = thumbSize(track, content.height());
        int offset = clampScroll(panel, content, scroll) * (track - size) / max;
        return Optional.of(new Rect(panel.rodX(), panel.viewTop() + offset, ROD_W, size));
    }

    /**
     * Drag or click-to-jump in the gutter: the thumb centre follows the pointer (SparkAssist {@code dragScroll}).
     * 在槽位中拖动或点击跳转：滑块中心跟随指针（同 SparkAssist {@code dragScroll}）。
     */
    public static int dragScroll(Panel panel, Content content, double mouseY) {
        int max = maxScroll(panel, content);
        if (max <= 0) {
            return 0;
        }
        int track = panel.viewHeight();
        int size = thumbSize(track, content.height());
        double fraction = (mouseY - panel.viewTop() - size / 2.0) / Math.max(1, track - size);
        return (int) Math.round(Math.max(0.0, Math.min(1.0, fraction)) * max);
    }

    private static int thumbSize(int track, int total) {
        return Math.min(track, Math.max(THUMB_MIN, track * track / Math.max(1, total)));
    }

    /**
     * Track y of a section tick: the header's content offset scaled onto the scrollbar track, kept inside
     * [trackTop, trackTop + trackHeight - 1]. Each section gets one 1 px tick in its colour, drawn under the rod.
     * 分区刻度在轨道上的 y：将标题的内容偏移按比例换算到滚动条轨道上，并限制在轨道内。每个分区一条 1 像素的分区色刻度，
     * 绘制于滑杆之下。
     */
    public static int tickY(int trackTop, int trackHeight, int contentHeight, int contentY) {
        int offset = (int) ((long) contentY * trackHeight / Math.max(1, contentHeight));
        return trackTop + Math.max(0, Math.min(trackHeight - 1, offset));
    }

    /**
     * A jump tab puts its section header at the viewport top (clamped). An absent section keeps the scroll.
     * 跳转标签将该分区标题滚动到视口顶部（受范围限制）；分区不存在时保持当前滚动。
     */
    public static int tabScroll(Panel panel, Content content, Section section, int scroll) {
        Block block = content.block(section);
        return block == null ? scroll : clampScroll(panel, content, block.headerY());
    }

    /**
     * The section owning the viewport top, for the ICON_ACTIVE tab wash; a header within the gap below the top
     * already counts.
     * 占据视口顶部的分区，用于标签的当前分区底色；标题位于顶部下方的间隔内时即视为当前分区。
     */
    public static @Nullable Section activeSection(Content content, int scroll) {
        Section active = null;
        for (Block block : content.blocks()) {
            if (block.headerY() <= scroll + SECTION_GAP) {
                active = block.section();
            }
        }
        return active;
    }

    /**
     * The running head: once a section header scrolls above the viewport it stays pinned at the top until the next
     * header pushes it out. {@code y} can sit above the viewport while it is pushed, so draw it scissored. Empty when
     * nothing is pinned.
     * 置顶标题：分区标题滚出视口顶部后固定在顶部，直到下一个标题将其推出。被推出时 {@code y} 可能高于视口，需在裁剪区内
     * 绘制。无置顶标题时为空。
     */
    public static Optional<RunningHead> runningHead(Panel panel, Content content, int scroll) {
        List<Block> blocks = content.blocks();
        int current = -1;
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).headerY() < scroll) {
                current = i;
            }
        }
        if (current < 0) {
            return Optional.empty();
        }
        int top = panel.viewTop();
        if (current + 1 < blocks.size()) {
            int next = headerY(panel, blocks.get(current + 1), scroll);
            top = Math.min(top, next - SECTION_GAP - HEADER_H);
        }
        if (top + HEADER_H <= panel.viewTop()) {
            return Optional.empty();
        }
        return Optional.of(new RunningHead(blocks.get(current), top));
    }

    /** Where the top fade starts: under the running head, else the viewport top. 顶部渐隐起点：置顶标题下方或视口顶边。 */
    public static int fadeTop(Panel panel, Content content, int scroll) {
        return runningHead(panel, content, scroll)
                .map(head -> Math.max(panel.viewTop(), head.bottom()))
                .orElse(panel.viewTop());
    }

    /**
     * Scroll just enough to show a cell below the running head. A hidden row lands flush under its pinned header;
     * a section's first row brings its own header to the top.
     * 仅滚动到恰好在置顶标题下方露出该单元。被遮住的行紧贴其置顶标题；分区首行会将自身标题带到顶部。
     */
    public static int reveal(Panel panel, Content content, int cell, int scroll) {
        int top = content.cellY(cell);
        int bottom = top + ROW_H;
        int next = clampScroll(panel, content, scroll);
        if (bottom > next + panel.viewHeight()) {
            next = clampScroll(panel, content, bottom - panel.viewHeight());
        }
        if (top < next + covered(panel, content, next)) {
            next = clampScroll(panel, content, top - HEADER_H);
        }
        return next;
    }

    private static int covered(Panel panel, Content content, int scroll) {
        return runningHead(panel, content, scroll).map(head -> head.bottom() - panel.viewTop()).orElse(0);
    }

    // ================================================================ keyboard

    /**
     * Arrow-key selection. Up/Down move one grid row, crossing sections to the nearest column; Left/Right move cell
     * by cell. Moves stop at the ends. With no selection ({@code cell < 0}) any move selects the first cell.
     * Returns -1 when there are no cells.
     * 方向键选择。上下键移动一行，跨分区时落到最近的列；左右键逐个单元移动。到达两端时停止。无选中项
     * （{@code cell < 0}）时任一方向键选中第一个单元。没有单元时返回 -1。
     */
    public static int move(Content content, int cell, Move move) {
        int count = content.cellCount();
        if (count == 0) {
            return -1;
        }
        if (cell < 0 || cell >= count) {
            return 0;
        }
        return switch (move) {
            case LEFT -> Math.max(0, cell - 1);
            case RIGHT -> Math.min(count - 1, cell + 1);
            case UP, DOWN -> vertical(content, cell, move == Move.DOWN);
        };
    }

    private static int vertical(Content content, int cell, boolean down) {
        List<Block> blocks = content.blocks();
        int columns = content.grid().columns();
        int index = content.blockIndex(cell);
        Block block = blocks.get(index);
        int local = cell - block.firstCell();
        int row = local / columns;
        int column = local % columns;
        if (down) {
            if (row + 1 < block.rows()) {
                return cellAt(block, row + 1, column, columns);
            }
            return index + 1 < blocks.size() ? cellAt(blocks.get(index + 1), 0, column, columns) : cell;
        }
        if (row > 0) {
            return cellAt(block, row - 1, column, columns);
        }
        if (index == 0) {
            return cell;
        }
        Block previous = blocks.get(index - 1);
        return cellAt(previous, previous.rows() - 1, column, columns);
    }

    private static int cellAt(Block block, int row, int column, int columns) {
        int inRow = Math.min(columns, block.count() - row * columns);
        return block.firstCell() + row * columns + Math.min(column, inRow - 1);
    }

    // ================================================================ tooltips and footer

    /**
     * Tooltip content box; a tooltip never covers its anchor, the footer or the rod. It hangs under the anchor and
     * flips above it when the box would cross the list viewport bottom (never onto the footer); it is clamped inside
     * the list area (never onto the rod), and an anchor in the right half right-aligns it to the anchor's right edge.
     * 提示框内容区，绝不遮住锚点、页脚或滑杆。悬挂于锚点下方；若会越过列表视口底边则翻到锚点上方（绝不覆盖页脚）；
     * 水平方向限制在列表区域内（绝不覆盖滑杆），锚点位于右半部分时与锚点右缘对齐。
     */
    public static Rect tooltip(Panel panel, Rect anchor, int width, int height) {
        int left = panel.listLeft();
        int right = panel.listRight();
        boolean rightHalf = 2 * anchor.x() + anchor.width() > left + right;
        int x = rightHalf ? anchor.right() - width : anchor.x();
        x = Math.max(left + TIP_PAD, Math.min(x, right - TIP_PAD - width));
        int y = anchor.bottom() + TIP_DROP;
        if (y + height + TIP_PAD > panel.viewBottom()) {
            y = anchor.y() - TIP_DROP - height;
        }
        return new Rect(x, y, width, height);
    }

    /**
     * The widest tooltip line that still fits inside the list area; longer lines are wrapped to it.
     * 列表区域内可容纳的最宽提示行；更长的行按此宽度换行。
     */
    public static int tooltipMaxWidth(Panel panel) {
        return Math.max(1, panel.listWidth() - 2 * TIP_PAD);
    }

    /** A cell's tooltip anchor: its name column to its right edge. 单元提示框锚点：从名称列到单元右缘。 */
    public static Rect nameAnchor(Rect cell) {
        return new Rect(cell.x() + CELL_TEXT_X, cell.y(), Math.max(0, cell.width() - CELL_TEXT_X), cell.height());
    }

    /** Vanilla tooltip height: 8 for one line, +2 after the first, +10 per further line. 原版提示框高度。 */
    public static int tooltipHeight(int lines) {
        return lines <= 0 ? 0 : TIP_LINE_H + (lines - 1) * TIP_LINE_PITCH + (lines > 1 ? TIP_FIRST_GAP : 0);
    }

    public static int tooltipLineY(int line) {
        return line <= 0 ? 0 : TIP_FIRST_GAP + line * TIP_LINE_PITCH;
    }

    /**
     * The footer, built right to left: the button, then the question or hint, then the keycap and forfeit note. An
     * instruction is never ellipsized: when the message does not fit beside the forfeit note, that note's text is
     * dropped (the Esc keycap stays); if it still does not fit, a question (data) is ellipsized to
     * {@link Footer#messageMaxWidth()}, but a hint is dropped whole and the forfeit note returns if it fits whole.
     * 页脚，自右向左排布：按钮、问句或提示、按键帽与放弃说明。说明文字绝不省略：消息放不下时先去掉放弃说明文字（保留
     * Esc 按键帽）；仍放不下时，问句（数据）按 {@link Footer#messageMaxWidth()} 省略，提示则整体省去，放弃说明若能完整
     * 放下则恢复显示。
     *
     * @param messageWidth the question or hint text width / 问句或提示的文字宽度
     * @param question     true for the selection question (drawn after a gem or bullet), false for the hint /
     *                     true 表示选中后的问句（前有宝石或圆点），false 表示提示
     */
    public static Footer footer(Panel panel, int keyLabelWidth, int forfeitWidth, int messageWidth, boolean question,
                                int buttonLabelWidth) {
        Rect f = panel.frame();
        int rowY = f.bottom() - FOOTER_ROW_Y;
        Rect keycap = new Rect(f.x() + FOOTER_INSET, rowY + KEYCAP_Y, keycapWidth(keyLabelWidth), KEYCAP_H);
        int width = buttonWidth(buttonLabelWidth);
        Rect button = new Rect(f.right() - FOOTER_INSET - width, rowY, width, BUTTON_H);
        int messageRight = button.x() - BUTTON_GAP;
        int forfeitX = keycap.right() + FORFEIT_GAP;
        int forfeitEnd = forfeitX + forfeitWidth;
        int bareLeft = keycap.right() + MESSAGE_GAP;
        int need = (question ? MARK_ADVANCE : 0) + messageWidth;
        int textY = rowY + FOOTER_TEXT_Y;
        if (messageRight - (forfeitEnd + MESSAGE_GAP) >= need) {
            return new Footer(keycap, textY, true, forfeitX, true, messageRight, messageWidth, false, button);
        }
        if (messageRight - bareLeft >= need) {
            return new Footer(keycap, textY, false, forfeitX, true, messageRight, messageWidth, false, button);
        }
        int room = messageRight - bareLeft - MARK_ADVANCE;
        if (question && room > 0) {
            return new Footer(keycap, textY, false, forfeitX, true, messageRight, room, true, button);
        }
        return new Footer(keycap, textY, forfeitEnd <= messageRight, forfeitX, false, messageRight, 0, false, button);
    }

    public static int keycapWidth(int labelWidth) {
        return KEYCAP_LABEL_X + labelWidth + KEYCAP_LABEL_X;
    }

    public static int buttonWidth(int labelWidth) {
        return BUTTON_LABEL_X + labelWidth + BUTTON_PAD_R;
    }

    // ================================================================ types

    public enum Mode {
        IDENTITY(IDENTITY_MAX_W, NAME_CAP_IDENTITY, MAX_COLS_IDENTITY),
        SURVIVAL(SURVIVAL_MAX_W, NAME_CAP_PLAYER, MAX_COLS_SURVIVAL);

        private final int maxWidth;
        private final int nameCap;
        private final int maxColumns;

        Mode(int maxWidth, int nameCap, int maxColumns) {
            this.maxWidth = maxWidth;
            this.nameCap = nameCap;
            this.maxColumns = maxColumns;
        }
    }

    public enum Move {
        UP,
        DOWN,
        LEFT,
        RIGHT
    }

    public enum HitKind {
        NONE,
        CELL,
        TAB,
        CLEAR,
        GUTTER,
        BUTTON
    }

    /** {@code cell} is set for CELL (else -1); {@code section} for CELL and TAB. CELL 时给出单元索引，TAB 时给出分区。 */
    public record Hit(HitKind kind, int cell, @Nullable Section section) {
        public static final Hit NONE = new Hit(HitKind.NONE, -1, null);
    }

    public record Rect(int x, int y, int width, int height) {
        /** The HUD table backing as a rect. HUD 表格底板对应的矩形。 */
        public static Rect ofHudBacking(TarotDivinationHudLayout.Geometry g) {
            return new Rect(g.backingLeft(), g.backingTop(),
                    g.backingRight() - g.backingLeft(), g.backingBottom() - g.backingTop());
        }

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
        }

        public boolean intersects(Rect other) {
            return width > 0 && height > 0 && other.width > 0 && other.height > 0
                    && x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
        }
    }

    public record Grid(int columns, int cellWidth) {
    }

    public record SectionSize(Section section, int count) {
    }

    /** One section: its header at {@code headerY}, then {@code rows} rows of cells. 一个分区：标题及其单元行。 */
    public record Block(Section section, int count, int headerY, int firstCell, int rows) {
        public int cellsTop() {
            return headerY + HEADER_H;
        }

        public int bottom() {
            return cellsTop() + rows * ROW_H;
        }
    }

    public record Content(Grid grid, List<Block> blocks, int height, int cellCount) {
        public Content {
            blocks = List.copyOf(blocks);
        }

        public @Nullable Block block(Section section) {
            for (Block block : blocks) {
                if (block.section() == section) {
                    return block;
                }
            }
            return null;
        }

        /** The block holding a valid cell index. 包含该有效单元索引的分区。 */
        public Block blockOf(int cell) {
            return blocks.get(blockIndex(cell));
        }

        int blockIndex(int cell) {
            for (int i = 0; i < blocks.size(); i++) {
                Block block = blocks.get(i);
                if (cell >= block.firstCell() && cell < block.firstCell() + block.count()) {
                    return i;
                }
            }
            throw new IndexOutOfBoundsException("cell " + cell + " of " + cellCount);
        }

        public int column(int cell) {
            return (cell - blockOf(cell).firstCell()) % grid.columns();
        }

        /** Content y of a cell's top edge. 单元顶边的内容 y。 */
        public int cellY(int cell) {
            Block block = blockOf(cell);
            return block.cellsTop() + (cell - block.firstCell()) / grid.columns() * ROW_H;
        }
    }

    /** {@code y} is the header block's screen top; it covers y..bottom() over the list. 置顶标题的屏幕顶边。 */
    public record RunningHead(Block block, int y) {
        public int bottom() {
            return y + HEADER_H;
        }
    }

    public record TitleBar(int iconX, int iconY, int titleX, int titleY, int titleMaxWidth, int paidX, int priceX,
                           int coinX) {
    }

    /**
     * The footer row. Every label, the keycap and button labels included, is drawn at {@code textY}. The message is
     * right-aligned at {@code messageRight}; the question's gem or bullet sits {@link #MARK_ADVANCE} px before its
     * text.
     * 页脚行。所有文字（含按键帽与按钮文字）绘制于 {@code textY}。消息右对齐于 {@code messageRight}；问句的宝石或圆点
     * 位于文字前 {@link #MARK_ADVANCE} 像素处。
     */
    public record Footer(Rect keycap, int textY, boolean forfeitShown, int forfeitX, boolean messageShown,
                         int messageRight, int messageMaxWidth, boolean messageEllipsized, Rect button) {
        public int keyLabelX() {
            return keycap.x() + KEYCAP_LABEL_X;
        }

        public int messageX(int shownWidth) {
            return messageRight - shownWidth;
        }

        public int markX(int shownWidth) {
            return messageX(shownWidth) - MARK_ADVANCE;
        }

        public int markY() {
            return button.y() + FOOTER_MARK_Y;
        }

        public int buttonLabelX() {
            return button.x() + BUTTON_LABEL_X;
        }
    }

    /**
     * The panel frame and every fixed sub-rect. Whether the HUD table is drawn again above the scrim is decided each
     * frame by the screen with {@link #clearOfHud}.
     * 面板外框与各固定子区域。是否在遮罩上方重绘 HUD 表格由界面每帧通过 {@link #clearOfHud} 判定。
     */
    public record Panel(Mode mode, Rect frame) {
        /** Identity: ends {@link #TABS_GAP} before the tabs; survival: the full width. 搜索凹槽。 */
        public Rect well() {
            int x = frame.x() + WELL_X;
            int right = mode == Mode.IDENTITY ? tabsLeft() - TABS_GAP : frame.right() - WELL_X;
            return new Rect(x, frame.y() + WELL_Y, Math.max(0, right - x), WELL_H);
        }

        /** Five jump tabs right-aligned to x+w-6 (identity only). 五个跳转标签，右对齐（仅身份模式）。 */
        public List<Rect> tabs() {
            if (mode != Mode.IDENTITY) {
                return List.of();
            }
            List<Rect> tabs = new ArrayList<>(TAB_SECTIONS.size());
            for (int i = 0; i < TAB_SECTIONS.size(); i++) {
                tabs.add(new Rect(tabsLeft() + i * (TAB_W + TAB_GAP), frame.y() + WELL_Y, TAB_W, TAB_H));
            }
            return tabs;
        }

        private int tabsLeft() {
            return frame.right() - WELL_X - TABS_W;
        }

        public int fieldX() {
            return frame.x() + FIELD_X;
        }

        public int fieldY() {
            return frame.y() + FIELD_Y;
        }

        /** The text field ends before the widest match count ("70/70"). 输入框止于最宽匹配计数之前。 */
        public int fieldWidth(int countReserveWidth) {
            return Math.max(1, countRight() - countReserveWidth - FIELD_COUNT_GAP - fieldX());
        }

        /** Room for the focused, shifted placeholder. 聚焦右移后占位文字的可用宽度。 */
        public int placeholderWidth() {
            return Math.max(0, well().right() - WELL_PAD_R - fieldX() - PLACEHOLDER_FOCUS_SHIFT);
        }

        public int clearX() {
            return well().right() - WELL_PAD_R - CLEAR_SIZE;
        }

        public int clearY() {
            return frame.y() + WELL_Y + CLEAR_Y;
        }

        /** 11x14 clear hit around the 5x5 ×, tested before the text field. 清除按钮热区，先于输入框判定。 */
        public Rect clearBox() {
            return new Rect(clearX() - CLEAR_HIT_PAD, frame.y() + WELL_Y, CLEAR_SIZE + 2 * CLEAR_HIT_PAD, WELL_H);
        }

        /** The match count is right-aligned here. 匹配计数右对齐于此。 */
        public int countRight() {
            return clearX() - COUNT_GAP;
        }

        public int viewTop() {
            return frame.y() + VIEW_TOP;
        }

        public int viewBottom() {
            return Math.max(viewTop(), frame.bottom() - FOOTER_H - VIEW_BOTTOM_PAD);
        }

        public int viewHeight() {
            return viewBottom() - viewTop();
        }

        /** The list scissor, x+3..x+w-3. 列表裁剪区域。 */
        public Rect viewport() {
            return new Rect(frame.x() + VIEW_INSET, viewTop(), Math.max(0, frame.width() - 2 * VIEW_INSET),
                    viewHeight());
        }

        public int listLeft() {
            return frame.x() + ROW_LEFT;
        }

        /** Cells end here, 1 px before the gutter. 单元止于此处，距槽位 1 像素。 */
        public int listRight() {
            return frame.right() - ROW_RIGHT - GUTTER;
        }

        public int listWidth() {
            return Math.max(0, listRight() - listLeft());
        }

        /** Scrollbar hit, the rightmost 5 px of the viewport. 滚动条热区，视口最右侧 5 像素。 */
        public Rect gutter() {
            return new Rect(viewport().right() - GUTTER, viewTop(), GUTTER, viewHeight());
        }

        /**
         * Rod x (3 px wide). The running head and the fades span viewport().x() .. rodX().
         * 滑杆 x（宽 3 像素）。置顶标题与渐隐覆盖 viewport().x() .. rodX()。
         */
        public int rodX() {
            return gutter().x();
        }

        public int headerMarkX() {
            return listLeft() + CELL_GEM_X;
        }

        /** Identity labels share the name column; the survival header has no mark. 身份模式标签与名称列对齐。 */
        public int headerLabelX() {
            return listLeft() + (mode == Mode.IDENTITY ? CELL_TEXT_X : CELL_GEM_X);
        }

        public int headerRight() {
            return listRight() - HEADER_RIGHT_PAD;
        }

        public int centerX() {
            return frame.x() + frame.width() / 2;
        }

        public int footerRuleY() {
            return frame.bottom() - FOOTER_RULE_Y;
        }
    }
}
