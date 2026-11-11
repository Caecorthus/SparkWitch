package dev.caecorthus.sparkwitch.client.sherry;

import dev.caecorthus.sparkwitch.client.gui.InventoryCardPaint;
import dev.caecorthus.sparkwitch.client.sherry.SherryDeductionLayout.Rect;
import dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryRules;
import net.minecraft.client.gui.DrawContext;

/**
 * Role-local tokens and fill primitives for 橘雪莉's 推演 panel: the Harpy Express house grammar (bevelled frame,
 * title band, ruled rows, select band, status pill, tag buttons) in a palette sampled from her badge art
 * ({@code textures/item/sherry_badge.png}): deep navy, gold trim, pale ice blue and one orange gem (owner pick
 * 2026-11-11, material B 「侦探徽章」 of the three shown). Shapes reuse {@link InventoryCardPaint} (which must stay
 * byte-identical with SparkTraits, so nothing is added there); sizes belong to {@link SherryDeductionLayout}, so what
 * is painted is what is hit-tested. Every primitive is fill-only: none batches and none draws text, so the screen
 * wraps them in {@link InventoryCardPaint#batch} and draws text after the batch.
 * 橘雪莉推演面板的职业自有令牌与填充原语：沿用哈比特快面板语法（斜面外框、标题带、带横线的行、选中带、状态牌、价签按钮），
 * 配色取自她的徽章贴图（{@code textures/item/sherry_badge.png}）：深藏青、金边、浅冰蓝与一颗橙色宝石（所有者 2026-11-11
 * 选定，三种候选中的材质 B「侦探徽章」）。图形复用 {@link InventoryCardPaint}（须与 SparkTraits 逐字节一致，因此不在其中
 * 添加任何内容）；尺寸归 {@link SherryDeductionLayout} 所有，绘制位置与命中判定一致。所有原语只做填充，既不自行分批也不
 * 绘制文字；界面用 {@link InventoryCardPaint#batch} 包裹，批次结束后再绘制文字。
 */
final class SherryDeductionPaint {
    /** Her identity colour, opaque. / 不透明的身份色。 */
    static final int ICE = 0xFF000000 | SherryRules.COLOR;

    /** 9 x 9 magnifier, lens ring only. 9 × 9 放大镜的镜框。 */
    private static final String[] LENS_RING = {
            "..###....", ".#...#...", "#.....#..", "#.....#..", "#.....#..", ".#...#...", "..###...."
    };
    private static final String[] LENS_GLASS = {
            ".........", "..###....", ".#####...", ".#####...", ".#####...", "..###...."
    };
    private static final String[] LENS_HANDLE = {
            ".........", ".........", ".........", ".........", ".........", ".........", "......#..", "......##.",
            ".......##"
    };
    /** 5 x 5 badge shield of the badge-lead mark. 徽章线索徽记的 5 × 5 盾形。 */
    private static final String[] SHIELD = {"#####", "#####", "#####", ".###.", "..#.."};
    /** 5 x 5 ring with a centre dot of the tracked mark. 追踪中徽记的 5 × 5 圆环加中心点。 */
    private static final String[] TARGET = {".###.", "#...#", "#.#.#", "#...#", ".###."};
    private static final String[] ARROW_LEFT = {"..#", ".##", "###", ".##", "..#"};
    /** 16 x 16 neutral head for a candidate without a player-list entry. 没有玩家列表条目的候选所用的 16 × 16 中性头像。 */
    private static final String[] NO_FACE = {
            "................", "......####......", ".....######.....", "....########....", "....########....",
            "....########....", ".....######.....", "......####......", ".......##.......", "....########....",
            "...##########...", "..############..", ".##############.", ".##############.", "################",
            "################"
    };

    // ================================================================ tokens

    /** The panel's one palette. 面板唯一的配色。 */
    static final Palette PALETTE = new Palette();

    /**
     * The badge palette's tokens (ARGB), named after the element they colour.
     * 徽章配色的全部令牌（ARGB），名称对应其着色的元素。
     */
    static final class Palette {
        private static final int GOLD = 0xFFF2D27E, GOLD_MID = 0xFFC9A552, GOLD_LO = 0xFF9C772A;
        // frame and band / 外框与标题带
        final int scrim = 0xA8030611, dropShadow = InventoryCardPaint.SHADOW, edge = 0xFF02040B, body = 0xFF0D1730;
        final int rimHi = 0xFF2B3E73, rimLo = 0xFF070C1C, rimMiter = 0xFF182448, ringTop = GOLD, ringBottom = GOLD_LO;
        final int band = 0xFF15254F, bandRule = GOLD_LO, title = GOLD;
        /** The rule line's strip under the title band. 标题带下方规则行的底色。 */
        final int strip = 0xFF080E20;
        // text / 文字
        final int text = 0xFFE8F1FB, textHi = 0xFFFFFFFF, muted = 0xFFA3B6D8, greyed = 0xFF62739A;
        // rules / 刻线
        final int etchTop = 0xFF02040B, etchBottom = 0x802B3E73, rowRule = 0x48C9A552;
        // rows / 行
        final int select = 0xFF1C3365, selectBar = GOLD, selectRim = 0xA065D0EE, hover = 0x1C80A0E0;
        final int faceFrame = GOLD_LO, faceFrameHot = GOLD, faceFrameSelected = ICE;
        final int faceWell = 0xFF070C1C, faceGlyph = 0xFF2B3E73, faceDim = 0xA80D1730;
        // chips / 徽记
        final int trackedFill = 0xFF113250, trackedBorder = ICE, trackedText = 0xFFB5ECFA;
        final int leadBorder = GOLD_MID, leadText = 0xFFF2E3B0, leadIcon = GOLD;
        // status pill / 状态牌
        final int pillBg = 0xFF091226, pillTop = GOLD, pillBottom = GOLD_LO, pillText = 0xFFE8F1FB, pillIcon = ICE;
        final int well = 0xFF070C1C, wellShade = 0xFF03060F, wellLip = 0xFF2B3E73;
        final int idleText = 0xFFA3B6D8, idleIcon = 0xFF8094B8;
        // buttons / 按钮
        final int buttonBg = 0xFF091226, buttonHover = 0xFF1B2D5C, buttonTopHot = 0xFFFFEDB5, buttonBottom = GOLD_LO;
        final int cancelTop = GOLD_MID, cancelLabel = 0xFFA3B6D8, labelHot = 0xFFFFFFFF, labelOff = 0xFF6B7DA0;
        final int primaryBg = 0xFF091226, primaryHover = 0xFF1B2D5C, primaryTop = GOLD, primaryTopHot = 0xFFFFEDB5;
        final int primaryBottom = GOLD_LO, primaryLabel = GOLD, primaryLabelHot = 0xFFFFFFFF;
        /** The one orange gem (the Confirm icon) and its empty socket. 唯一的橙色宝石（确认按钮图标）及其空宝石座。 */
        final int gem = 0xFFF0892B, iconOff = 0xFF4A5A80;
        // price chip / 价格徽章
        final int priceBg = 0xFF091226, priceTop = GOLD, priceBottom = GOLD_LO, priceText = GOLD;
        // pager / 翻页
        final int arrowOutline = GOLD_LO, arrowOutlineOff = 0xFF2B3E73;
        final int arrowGlyph = 0xFFA3B6D8, arrowGlyphHot = 0xFFFFFFFF, arrowGlyphOff = 0xFF4A5A80;
        // tooltip / 提示框
        final int tipBg = 0xFF091226, tipTop = GOLD, tipBottom = GOLD_LO, tipText = 0xFFE8F1FB;
        // title glyph / 标题图标
        final int glyphRing = ICE, glyphGlass = 0x4865D0EE, glyphGlint = 0xFFFFFFFF, glyphHandle = GOLD;

        private Palette() {
        }
    }

    private SherryDeductionPaint() {
    }

    // ================================================================ frame

    /**
     * Footprint (x, y)..(x+w+1, y+h+1): the house panel recipe (InventoryCardPaint.panel) in the badge tokens.
     * L-shaped drop shadow, 1 px edge outline with cut corners, body, 1 px bevel, then a 1 px gold ring. Content
     * starts at +3.
     * 哈比特快面板配方（InventoryCardPaint.panel）换用徽章配色：L 形投影、切角的 1 像素描边、底色、1 像素斜面、一圈 1 像素
     * 金线。内容自 +3 起。
     */
    static void frame(DrawContext c, Rect r) {
        int x = r.x(), y = r.y(), w = r.width(), h = r.height();
        c.fill(x + w, y + 2, x + w + 1, y + h, PALETTE.dropShadow);
        c.fill(x + 2, y + h, x + w, y + h + 1, PALETTE.dropShadow);
        InventoryCardPaint.roundedOutline(c, x, y, w, h, PALETTE.edge);
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, PALETTE.body);
        c.fill(x + 1, y + 1, x + w - 1, y + 2, PALETTE.rimHi);
        c.fill(x + 1, y + 2, x + 2, y + h - 1, PALETTE.rimHi);
        c.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, PALETTE.rimLo);
        c.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, PALETTE.rimLo);
        c.fill(x + w - 2, y + 1, x + w - 1, y + 2, PALETTE.rimMiter);
        c.fill(x + 1, y + h - 2, x + 2, y + h - 1, PALETTE.rimMiter);
        c.fill(x + 2, y + 2, x + w - 2, y + 3, PALETTE.ringTop);
        c.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, PALETTE.ringBottom);
        c.fillGradient(x + 2, y + 3, x + 3, y + h - 3, PALETTE.ringTop, PALETTE.ringBottom);
        c.fillGradient(x + w - 3, y + 3, x + w - 2, y + h - 3, PALETTE.ringTop, PALETTE.ringBottom);
    }

    /** Footprint (x+3, y+3)..(right-3, y+21): the band, then a gold rule and an edge rule. 标题带及其下两条刻线。 */
    static void titleBand(DrawContext c, Rect r) {
        int x1 = r.x() + SherryDeductionLayout.BODY_INSET, x2 = r.right() - SherryDeductionLayout.BODY_INSET;
        c.fill(x1, r.y() + SherryDeductionLayout.BAND_TOP, x2, r.y() + SherryDeductionLayout.BAND_BOTTOM, PALETTE.band);
        c.fill(x1, r.y() + SherryDeductionLayout.BAND_BOTTOM, x2, r.y() + SherryDeductionLayout.BAND_BOTTOM + 1,
                PALETTE.bandRule);
        c.fill(x1, r.y() + SherryDeductionLayout.BAND_BOTTOM + 1, x2, r.y() + SherryDeductionLayout.BAND_END,
                PALETTE.edge);
    }

    /** Footprint 9x9 at (x, y): the detective's magnifier (ice ring, tinted glass, glint, gold handle). 9 × 9 侦探放大镜。 */
    static void magnifier(DrawContext c, int x, int y) {
        InventoryCardPaint.bitmap(c, x, y, PALETTE.glyphGlass, LENS_GLASS);
        InventoryCardPaint.bitmap(c, x, y, PALETTE.glyphRing, LENS_RING);
        InventoryCardPaint.bitmap(c, x, y, PALETTE.glyphHandle, LENS_HANDLE);
        c.fill(x + 2, y + 2, x + 3, y + 3, PALETTE.glyphGlint);
    }

    /** Footprint = rect: the raised price tag in the title band. 标题带内的凸起价签。 */
    static void priceChip(DrawContext c, Rect r) {
        raised(c, r, PALETTE.priceBg, PALETTE.priceTop, PALETTE.priceBottom);
    }

    /**
     * Footprint (x+3, y+21)..(right-3, ruleY+2): the rule line's strip, one tone off the body, closed by an etched
     * rule across the whole panel.
     * 规则行的底色条，与正文底色相差一档，下方以横贯面板的刻线收尾。
     */
    static void hintStrip(DrawContext c, Rect r, int ruleY) {
        int x1 = r.x() + SherryDeductionLayout.BODY_INSET;
        int x2 = r.right() - SherryDeductionLayout.BODY_INSET;
        c.fill(x1, r.y() + SherryDeductionLayout.BAND_END, x2, ruleY, PALETTE.strip);
        etched(c, x1, x2, ruleY);
    }

    /** Footprint (x1, y)..(x2, y+2): the etched rule under the rule line and above the footer. 规则行下方与页脚上方的刻线。 */
    static void etched(DrawContext c, int x1, int x2, int y) {
        c.fill(x1, y, x2, y + 1, PALETTE.etchTop);
        c.fill(x1, y + 1, x2, y + 2, PALETTE.etchBottom);
    }

    // ================================================================ rows

    /**
     * Footprint = the cell minus its last row (the ruled line). Selected = the select band with a rim line top and
     * bottom and a {@link SherryDeductionLayout#BAR_W} bar on the left; else hovered = the hover wash. The bar means
     * "selected" and nothing else.
     * 范围为单元去掉最后一行（行下横线）。选中：选中带、上下各一条描边线、左侧竖条；否则悬停时为淡色。竖条只表示“已选中”。
     */
    static void rowState(DrawContext c, Rect r, boolean hovered, boolean selected) {
        int bottom = r.bottom() - 1;
        if (selected) {
            c.fill(r.x(), r.y(), r.right(), bottom, PALETTE.select);
            c.fill(r.x(), r.y(), r.right(), r.y() + 1, PALETTE.selectRim);
            c.fill(r.x(), bottom - 1, r.right(), bottom, PALETTE.selectRim);
            c.fill(r.x(), r.y(), r.x() + SherryDeductionLayout.BAR_W, bottom, PALETTE.selectBar);
        } else if (hovered) {
            c.fill(r.x(), r.y(), r.right(), bottom, PALETTE.hover);
        }
    }

    /** Footprint (cell.x + ROW_RULE_X, bottom-1)..(right, bottom). 行下横线。 */
    static void rowRule(DrawContext c, Rect r) {
        c.fill(r.x() + SherryDeductionLayout.ROW_RULE_X, r.bottom() - 1, r.right(), r.bottom(), PALETTE.rowRule);
    }

    /**
     * Footprint FACE_FRAME_SIZE square at the cell's face-frame position: a cut-corner gold frame, brighter when
     * hovered and ice blue when selected. {@code placeholder} adds the dark well and the neutral head for a candidate
     * without a player-list entry.
     * 头像框：切角金色外框，悬停时变亮，选中时为冰蓝。{@code placeholder} 为没有玩家列表条目的候选补上深色底与中性头像。
     */
    static void face(DrawContext c, Rect cell, boolean hovered, boolean selected, boolean placeholder) {
        int x = cell.x() + SherryDeductionLayout.FACE_FRAME_X, y = cell.y() + SherryDeductionLayout.FACE_FRAME_Y;
        int size = SherryDeductionLayout.FACE_FRAME_SIZE;
        InventoryCardPaint.roundedOutline(c, x, y, size, size,
                selected ? PALETTE.faceFrameSelected : hovered ? PALETTE.faceFrameHot : PALETTE.faceFrame);
        if (placeholder) {
            int fx = cell.x() + SherryDeductionLayout.FACE_X, fy = cell.y() + SherryDeductionLayout.FACE_Y;
            c.fill(fx, fy, fx + SherryDeductionLayout.FACE_SIZE, fy + SherryDeductionLayout.FACE_SIZE,
                    PALETTE.faceWell);
            InventoryCardPaint.bitmap(c, fx, fy, PALETTE.faceGlyph, NO_FACE);
        }
    }

    /** Footprint FACE_SIZE square over the face: dims a candidate who cannot be picked. 不可选候选的头像淡化层。 */
    static void faceDim(DrawContext c, Rect cell) {
        int x = cell.x() + SherryDeductionLayout.FACE_X, y = cell.y() + SherryDeductionLayout.FACE_Y;
        c.fill(x, y, x + SherryDeductionLayout.FACE_SIZE, y + SherryDeductionLayout.FACE_SIZE, PALETTE.faceDim);
    }

    /**
     * Footprint = rect (CHIP_H tall): the 追踪中 chip of a target already on the 20 s cycle, a filled tag with an ice
     * rim and its ring glyph at (x+CHIP_PAD, y+CHIP_ICON_Y). Filled = not selectable.
     * 已处于 20 秒周期目标的「追踪中」徽记：带冰蓝描边的实心标签，圆环图形位于 (x+CHIP_PAD, y+CHIP_ICON_Y)。实心表示不可选择。
     */
    static void trackedChip(DrawContext c, Rect r) {
        InventoryCardPaint.roundedFill(c, r.x(), r.y(), r.width(), r.height(), PALETTE.trackedFill);
        InventoryCardPaint.roundedOutline(c, r.x(), r.y(), r.width(), r.height(), PALETTE.trackedBorder);
        mark(c, r.x() + SherryDeductionLayout.CHIP_PAD, r.y() + SherryDeductionLayout.CHIP_ICON_Y, true);
    }

    /**
     * Footprint = rect: the hollow badge-lead chip of a badge killer on the slower 40 s cycle, with the shield at
     * (x+CHIP_PAD, y+CHIP_ICON_Y). Hollow = still selectable.
     * 处于较慢的 40 秒周期的徽章凶手所用的空心「线索」徽记，盾形位于 (x+CHIP_PAD, y+CHIP_ICON_Y)。空心表示仍可选择。
     */
    static void leadChip(DrawContext c, Rect r) {
        InventoryCardPaint.roundedOutline(c, r.x(), r.y(), r.width(), r.height(), PALETTE.leadBorder);
        mark(c, r.x() + SherryDeductionLayout.CHIP_PAD, r.y() + SherryDeductionLayout.CHIP_ICON_Y, false);
    }

    /**
     * Footprint 5x5 at (x, y): the mark's own glyph, a ring with a centre dot for 追踪中 and a shield for a badge
     * lead, so the two survive grayscale. Also drawn alone, before the label, when the chips sit under the names.
     * 徽记自身的图形：「追踪中」为带中心点的圆环，徽章线索为盾形，灰度下仍可区分。徽记位于名字下方时单独绘制在文字之前。
     */
    static void mark(DrawContext c, int x, int y, boolean tracked) {
        InventoryCardPaint.bitmap(c, x, y, tracked ? PALETTE.trackedText : PALETTE.leadIcon, tracked ? TARGET : SHIELD);
    }

    static int markColor(boolean tracked) {
        return tracked ? PALETTE.trackedText : PALETTE.leadText;
    }

    // ================================================================ pager

    /**
     * Footprint = rect: a pager arrow box; enabled hover washes it, disabled is dim and ignores hover.
     * 翻页箭头框；可用时悬停加淡色，不可用时变暗且忽略悬停。
     */
    static void arrow(DrawContext c, Rect r, boolean left, boolean enabled, boolean hovered) {
        InventoryCardPaint.roundedOutline(c, r.x(), r.y(), r.width(), r.height(),
                enabled ? PALETTE.arrowOutline : PALETTE.arrowOutlineOff);
        boolean hot = enabled && hovered;
        if (hot) {
            c.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.bottom() - 1, PALETTE.hover);
        }
        int color = !enabled ? PALETTE.arrowGlyphOff : hot ? PALETTE.arrowGlyphHot : PALETTE.arrowGlyph;
        int gx = r.x() + (r.width() - 3) / 2;
        int gy = r.y() + (r.height() - 5) / 2;
        if (left) {
            InventoryCardPaint.bitmap(c, gx, gy, color, ARROW_LEFT);
        } else {
            InventoryCardPaint.play(c, gx, gy, color);
        }
    }

    // ================================================================ footer

    /**
     * Footprint = rect (PILL_H tall). Selected = a raised tag with a check; nothing selected = a sunken groove with a
     * hollow diamond. The shape carries the state, the colour only adds weight.
     * 已选择为带勾的凸起标签；未选择为带空心菱形的凹槽。形状表达状态，颜色只作强调。
     */
    static void pill(DrawContext c, Rect r, boolean selected) {
        int iconX = r.x() + SherryDeductionLayout.PILL_PAD, iconY = r.y() + SherryDeductionLayout.PILL_ICON_Y;
        if (selected) {
            raised(c, r, PALETTE.pillBg, PALETTE.pillTop, PALETTE.pillBottom);
            InventoryCardPaint.check(c, iconX, iconY, PALETTE.pillIcon);
        } else {
            sunken(c, r);
            InventoryCardPaint.diamondOutline(c, iconX, iconY, PALETTE.idleIcon);
        }
    }

    /** Footprint = rect: the quiet Cancel tag; hover warms the body and lights the rim. 取消按钮；悬停时加深底色、点亮边缘。 */
    static void cancelButton(DrawContext c, Rect r, boolean hovered) {
        raised(c, r, hovered ? PALETTE.buttonHover : PALETTE.buttonBg,
                hovered ? PALETTE.buttonTopHot : PALETTE.cancelTop, PALETTE.buttonBottom);
    }

    static int cancelLabelColor(boolean hovered) {
        return hovered ? PALETTE.labelHot : PALETTE.cancelLabel;
    }

    /**
     * Footprint = rect: the paid Confirm with the orange gem at (x+BUTTON_ICON_X, y+BUTTON_ICON_Y). Enabled = the
     * raised tag with the gem lit (hover warms the body and lights the top edge); disabled = a sunken slot with an
     * empty socket, hover ignored.
     * 付费的确认按钮，橙色宝石位于 (x+BUTTON_ICON_X, y+BUTTON_ICON_Y)。可用时为凸起价签且宝石点亮（悬停加深底色、点亮顶边）；
     * 不可用时为凹槽加空宝石座，忽略悬停。
     */
    static void confirmButton(DrawContext c, Rect r, boolean enabled, boolean hovered) {
        int iconX = r.x() + SherryDeductionLayout.BUTTON_ICON_X, iconY = r.y() + SherryDeductionLayout.BUTTON_ICON_Y;
        if (enabled) {
            raised(c, r, hovered ? PALETTE.primaryHover : PALETTE.primaryBg,
                    hovered ? PALETTE.primaryTopHot : PALETTE.primaryTop, PALETTE.primaryBottom);
        } else {
            sunken(c, r);
        }
        gem(c, iconX, iconY, PALETTE.gem, enabled ? PALETTE.primaryTop : PALETTE.iconOff, enabled);
    }

    static int confirmLabelColor(boolean enabled, boolean hovered) {
        if (!enabled) {
            return PALETTE.labelOff;
        }
        return hovered ? PALETTE.primaryLabelHot : PALETTE.primaryLabel;
    }

    /**
     * Footprint 5x5 at (x, y): the house gem (bezel with cut corners, 3x3 core, glint, shade); {@code lit} false
     * leaves an empty socket.
     * 5 × 5 宝石：切角镶边、3 × 3 核心、左上高光、右下暗角；{@code lit} 为 false 时只留空宝石座。
     */
    private static void gem(DrawContext c, int x, int y, int core, int bezel, boolean lit) {
        c.fill(x + 1, y, x + 4, y + 1, bezel);
        c.fill(x + 1, y + 4, x + 4, y + 5, bezel);
        c.fill(x, y + 1, x + 1, y + 4, bezel);
        c.fill(x + 4, y + 1, x + 5, y + 4, bezel);
        if (lit) {
            c.fill(x + 1, y + 1, x + 4, y + 4, core);
            c.fill(x + 1, y + 1, x + 2, y + 2, InventoryCardPaint.mix(core, 0xFFFFFFFF, 0.55));
            c.fill(x + 3, y + 3, x + 4, y + 4, InventoryCardPaint.mix(core, 0xFF000000, 0.35));
        }
    }

    // ================================================================ tooltip

    /** Vanilla tooltip geometry in the badge colours; (x, y, w, h) is the content box. 原版提示框几何，徽章配色。 */
    static void tooltip(DrawContext c, Rect box) {
        int x = box.x(), y = box.y(), w = box.width(), h = box.height();
        c.fill(x - 3, y - 4, x + w + 3, y - 3, PALETTE.tipBg);
        c.fill(x - 3, y + h + 3, x + w + 3, y + h + 4, PALETTE.tipBg);
        c.fill(x - 3, y - 3, x + w + 3, y + h + 3, PALETTE.tipBg);
        c.fill(x - 4, y - 3, x - 3, y + h + 3, PALETTE.tipBg);
        c.fill(x + w + 3, y - 3, x + w + 4, y + h + 3, PALETTE.tipBg);
        c.fillGradient(x - 3, y - 2, x - 2, y + h + 2, PALETTE.tipTop, PALETTE.tipBottom);
        c.fillGradient(x + w + 2, y - 2, x + w + 3, y + h + 2, PALETTE.tipTop, PALETTE.tipBottom);
        c.fill(x - 3, y - 3, x + w + 3, y - 2, PALETTE.tipTop);
        c.fill(x - 3, y + h + 2, x + w + 3, y + h + 3, PALETTE.tipBottom);
    }

    // ================================================================ recipes

    /** The house raised tag at any size: body, a lit top line, a darker bottom line, gradient sides. 任意尺寸的凸起价签。 */
    private static void raised(DrawContext c, Rect r, int fill, int top, int bottom) {
        InventoryCardPaint.roundedFill(c, r.x(), r.y(), r.width(), r.height(), fill);
        c.fill(r.x() + 1, r.y(), r.right() - 1, r.y() + 1, top);
        c.fill(r.x() + 1, r.bottom() - 1, r.right() - 1, r.bottom(), bottom);
        c.fillGradient(r.x(), r.y() + 1, r.x() + 1, r.bottom() - 1, top, bottom);
        c.fillGradient(r.right() - 1, r.y() + 1, r.right(), r.bottom() - 1, top, bottom);
    }

    /** The house sunken groove at any size: well, a shade top row, a lit bottom lip. 任意尺寸的凹槽。 */
    private static void sunken(DrawContext c, Rect r) {
        InventoryCardPaint.roundedFill(c, r.x(), r.y(), r.width(), r.height(), PALETTE.well);
        c.fill(r.x() + 1, r.y(), r.right() - 1, r.y() + 1, PALETTE.wellShade);
        c.fill(r.x() + 1, r.bottom() - 1, r.right() - 1, r.bottom(), PALETTE.wellLip);
    }
}
