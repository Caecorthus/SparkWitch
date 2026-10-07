package dev.caecorthus.sparkwitch.client.tarot;

import dev.caecorthus.sparkwitch.client.gui.InventoryCardPaint;
import dev.caecorthus.sparkwitch.net.OpenTarotDivinationSelectorS2CPacket;
import java.util.List;
import net.minecraft.client.gui.DrawContext;

/**
 * Role-local Harpy Express tokens and fill primitives for the Tarot Reader ledger (selector and reading slip).
 * InventoryCardPaint must stay byte-identical with SparkTraits, so the few SparkAssist ExpressPalette tokens and
 * ExpressPaint recipes it lacks are copied here value for value. Sizes and width rules belong to
 * {@link TarotLedgerLayout}, so what is painted is what is hit-tested. Every primitive is fill-only: none batches and
 * none draws text, so callers wrap them in {@link InventoryCardPaint#batch} and draw text after the batch.
 * 塔罗牌师占卜台账（选择界面与结果纸条）的职业自有令牌与填充原语。InventoryCardPaint 须与 SparkTraits 逐字节一致，
 * 因此它缺少的 SparkAssist ExpressPalette 令牌与 ExpressPaint 配方按原值复制于此。尺寸与宽度规则归
 * {@link TarotLedgerLayout} 所有，绘制位置与命中判定一致。所有原语只做填充，既不自行分批也不绘制文字；调用方用
 * {@link InventoryCardPaint#batch} 包裹，批次结束后再绘制文字。
 */
public final class TarotLedgerPaint {
    // ---- tokens (values = SparkAssist ExpressPalette) ----
    public static final int BAND = 0xFF381406, SELECT = 0xFF4E2614, WELL_LIP = 0xFF3D1E0E, ETCH_LIGHT = 0x80542818;
    public static final int THUMB_SHADE = 0xFF5E3F0C, BUTTON_HOVER = 0xFF3A1A0B;
    public static final int ICON_HOVER = 0x30FFBF49, ICON_ACTIVE = 0x18FFBF49;
    /**
     * Lighter than Assist's modal scrim (0xC4): the round keeps running behind the selector, so the world stays
     * readable.
     * 比 Assist 模态遮罩（0xC4）更浅：选择时对局仍在进行，世界画面需保持可读。
     */
    public static final int LEDGER_SCRIM = 0xA8070302;
    /**
     * Special-section colour, the same value SparkFactionAPI's listroles uses for its special header (private there).
     * 特殊分区颜色，与 SparkFactionAPI listroles 特殊分区标题同值（该常量在其内部为私有）。
     */
    public static final int SPECIAL = 0xFF79C7D4;

    private static final int TICK_ALPHA = 0xC0000000;
    /** The HUD's empty-faction grammar (TarotDivinationHudRenderer ZERO_PIP_ALPHA). 与占卜表空阵营描边同一语法。 */
    private static final int ZERO_ALPHA = 0x80000000;
    private static final double GEM_MIN_CONTRAST = 2.0;
    private static final double GEM_LIFT = 0.35;

    private static final String[] MAGNIFIER = {
            ".####....", "#....#...", "#....#...", "#....#...", "#....#...", ".######..", ".....###.", "......###",
            ".......##"
    };
    private static final String[] CROSS = {"#...#", ".#.#.", "..#..", ".#.#.", "#...#"};

    private TarotLedgerPaint() {
    }

    // ---- frame ----
    /** Footprint (x+3, y+3)..(x+w-3, y+21): BAND to y+19, then a BRASS_LO rule at y+19 and an EDGE rule at y+20. */
    public static void titleBand(DrawContext c, int x, int y, int w) {
        c.fill(x + 3, y + 3, x + w - 3, y + 19, BAND);
        c.fill(x + 3, y + 19, x + w - 3, y + 20, InventoryCardPaint.BRASS_LO);
        c.fill(x + 3, y + 20, x + w - 3, y + 21, InventoryCardPaint.EDGE);
    }

    /** Footprint (x1, y)..(x2, y+2): EDGE line over an ETCH_LIGHT line (the footer rule). */
    public static void etched(DrawContext c, int x1, int x2, int y) {
        c.fill(x1, y, x2, y + 1, InventoryCardPaint.EDGE);
        c.fill(x1, y + 1, x2, y + 2, ETCH_LIGHT);
    }

    /**
     * Footprint (x1, y)..(x2, y+h): selected = SELECT with a 2 px COIN bar on the left, else hovered = HOVER wash.
     * The coin bar means "selected" and nothing else. 金币色竖条只表示“已选中”。
     */
    public static void rowState(DrawContext c, int x1, int y, int x2, int h, boolean hovered, boolean selected) {
        if (selected) {
            c.fill(x1, y, x2, y + h, SELECT);
            c.fill(x1, y, x1 + 2, y + h, InventoryCardPaint.COIN);
        } else if (hovered) {
            c.fill(x1, y, x2, y + h, InventoryCardPaint.HOVER);
        }
    }

    // ---- search ----
    /**
     * Footprint (x, y)..(x+w, y+h), or (x-1, y-1)..(x+w+1, y+h+1) with the BRASS_HI focus ring; includes the FAINT
     * magnifier at MAGNIFIER_INSET. 搜索凹槽，含左侧放大镜；聚焦时外扩 1 像素黄铜描边。
     */
    public static void searchWell(DrawContext c, int x, int y, int w, int h, boolean focused) {
        if (focused) {
            InventoryCardPaint.roundedOutline(c, x - 1, y - 1, w + 2, h + 2, InventoryCardPaint.BRASS_HI);
        }
        c.fill(x, y, x + w, y + h, InventoryCardPaint.WELL);
        c.fill(x, y, x + w, y + 1, InventoryCardPaint.WELL_SHADE);
        c.fill(x, y + h - 1, x + w, y + h, WELL_LIP);
        int inset = TarotLedgerLayout.MAGNIFIER_INSET;
        magnifier(c, x + inset, y + inset, InventoryCardPaint.FAINT);
    }

    /** Footprint 9x9 at (x, y). */
    public static void magnifier(DrawContext c, int x, int y, int col) {
        InventoryCardPaint.bitmap(c, x, y, col, MAGNIFIER);
    }

    /** Footprint 5x5 at (x, y): the clear-query cross. */
    public static void clear5(DrawContext c, int x, int y, int col) {
        InventoryCardPaint.bitmap(c, x, y, col, CROSS);
    }

    // ---- scrolling ----
    /**
     * A section start on the scrollbar track: {@code contentY} is the header's content offset, {@code rgb} 0xRRGGBB.
     * 滚动条轨道上的分区起点：contentY 为分区标题的内容偏移，rgb 为分区颜色。
     */
    public record Tick(int contentY, int rgb) {
    }

    /**
     * Footprint (gx, vy)..(gx+ROD_W, vy+vh): EDGE track down the middle, then one 1 px tick per section across the
     * gutter at alpha 0xC0 (placed by {@link TarotLedgerLayout#tickY}), then the rod (gx, ty)..(gx+ROD_W, ty+th) over
     * them; hot = hovered or dragged.
     * 先画轨道线，再画各分区的 1 像素刻度（alpha 0xC0，位置由 TarotLedgerLayout.tickY 计算），最后在其上画滑杆。
     */
    public static void scrollbarDark(DrawContext c, int gx, int vy, int vh, int ty, int th, boolean hot,
                                     int total, List<Tick> ticks) {
        int right = gx + TarotLedgerLayout.ROD_W;
        c.fill(gx + 1, vy, right - 1, vy + vh, InventoryCardPaint.EDGE);
        for (Tick tick : ticks) {
            int y = TarotLedgerLayout.tickY(vy, vh, total, tick.contentY());
            c.fill(gx, y, right, y + 1, TICK_ALPHA | (tick.rgb() & 0xFFFFFF));
        }
        int body = hot ? InventoryCardPaint.BRASS : InventoryCardPaint.BRASS_LO;
        int light = hot ? InventoryCardPaint.BRASS_HI : InventoryCardPaint.BRASS;
        int shade = hot ? InventoryCardPaint.BRASS_LO : THUMB_SHADE;
        c.fill(gx, ty, right, ty + th, body);
        c.fill(gx, ty, gx + 1, ty + th, light);
        c.fill(gx, ty, right, ty + 1, light);
        c.fill(right - 1, ty + 1, right, ty + th, shade);
        c.fill(gx + 1, ty + th - 1, right, ty + th, shade);
    }

    /**
     * Footprint (x1, y)..(x2, y+FADE_H): BODY fading to transparent downwards. Drawn at local z 0; the screen lifts it
     * with the running head in one translate(0, 0, 1), because list glyphs sit at +0.03 and a same-z fill cannot cover
     * them.
     * 本地 z 为 0；界面将其与置顶分区标题一起平移到 z+1，因为列表文字位于 +0.03，同层填充无法盖住它们。
     */
    public static void fadeTop(DrawContext c, int x1, int y, int x2) {
        c.fillGradient(x1, y, x2, y + TarotLedgerLayout.FADE_H, InventoryCardPaint.BODY,
                InventoryCardPaint.BODY & 0xFFFFFF);
    }

    /** Footprint (x1, y2-FADE_H)..(x2, y2): transparent fading to BODY; same z contract as {@link #fadeTop}. */
    public static void fadeBottom(DrawContext c, int x1, int y2, int x2) {
        c.fillGradient(x1, y2 - TarotLedgerLayout.FADE_H, x2, y2, InventoryCardPaint.BODY & 0xFFFFFF,
                InventoryCardPaint.BODY);
    }

    // ---- footer ----
    /**
     * Footprint (x, y)..(x+w, y+KEYCAP_H): sunken pill, w from {@link TarotLedgerLayout#keycapWidth}; the caller draws
     * the label at (x+KEYCAP_LABEL_X, y+KEYCAP_LABEL_Y) in FAINT without shadow.
     */
    public static void keycap(DrawContext c, int x, int y, int w) {
        sunkenPill(c, x, y, w, TarotLedgerLayout.KEYCAP_H);
    }

    /** Label colour for {@link #tagButton}, drawn at (x+BUTTON_LABEL_X, y+BUTTON_LABEL_Y) without shadow. */
    public static int tagButtonLabelColor(boolean enabled, boolean hovered) {
        if (!enabled) {
            return InventoryCardPaint.FAINT;
        }
        return hovered ? InventoryCardPaint.TEXT_HI : InventoryCardPaint.POLISHED;
    }

    /**
     * Footprint (x, y)..(x+w, y+BUTTON_H), w from {@link TarotLedgerLayout#buttonWidth}, with the play icon at
     * (x+BUTTON_ICON_X, y+BUTTON_ICON_Y). Enabled = the raised pill recipe (hover warms the body to BUTTON_HOVER and
     * coins the top edge); disabled = the sunken slot with a BRASS_LO icon, hover ignored.
     * 启用时为凸起价签（悬停加深底色、顶边变金币色）；禁用时为凹槽，忽略悬停。
     */
    public static void tagButton(DrawContext c, int x, int y, int w, boolean enabled, boolean hovered) {
        int h = TarotLedgerLayout.BUTTON_H;
        int iconX = x + TarotLedgerLayout.BUTTON_ICON_X;
        int iconY = y + TarotLedgerLayout.BUTTON_ICON_Y;
        if (!enabled) {
            sunkenPill(c, x, y, w, h);
            InventoryCardPaint.play(c, iconX, iconY, InventoryCardPaint.BRASS_LO);
            return;
        }
        int top = hovered ? InventoryCardPaint.COIN : InventoryCardPaint.BRASS_HI;
        int bottom = InventoryCardPaint.BRASS_LO;
        InventoryCardPaint.roundedFill(c, x, y, w, h, hovered ? BUTTON_HOVER : InventoryCardPaint.TIP_BG);
        c.fill(x + 1, y, x + w - 1, y + 1, top);
        c.fill(x + 1, y + h - 1, x + w - 1, y + h, bottom);
        c.fillGradient(x, y + 1, x + 1, y + h - 1, top, bottom);
        c.fillGradient(x + w - 1, y + 1, x + w, y + h - 1, top, bottom);
        InventoryCardPaint.play(c, iconX, iconY, InventoryCardPaint.COIN);
    }

    /** InventoryCardPaint.pill's sunken recipe at any height: WELL, WELL_SHADE top row, RIM_HI bottom lip. */
    private static void sunkenPill(DrawContext c, int x, int y, int w, int h) {
        InventoryCardPaint.roundedFill(c, x, y, w, h, InventoryCardPaint.WELL);
        c.fill(x + 1, y, x + w - 1, y + 1, InventoryCardPaint.WELL_SHADE);
        c.fill(x + 1, y + h - 1, x + w - 1, y + h, InventoryCardPaint.RIM_HI);
    }

    // ---- marks ----
    /**
     * Footprint (x, y)..(x+6, y+6): a filled 5x5 diamond in {@code rgb} (alpha forced opaque) over its (+1, +1)
     * vanilla-shadow copy, exactly the HUD pip. Zero = the HUD's empty-faction outline at alpha 0x80 with its shadow.
     * Special sections pass {@link #SPECIAL}.
     * 与占卜表圆点相同：实心菱形加 (+1, +1) 原版阴影；zero 时使用占卜表空阵营语法（alpha 0x80 描边及其阴影）。
     */
    public static void sectionMark(DrawContext c, int x, int y, int rgb, boolean zero) {
        if (zero) {
            int outline = ZERO_ALPHA | (rgb & 0xFFFFFF);
            InventoryCardPaint.diamondOutline(c, x + 1, y + 1, shadow(outline));
            InventoryCardPaint.diamondOutline(c, x, y, outline);
        } else {
            int fill = 0xFF000000 | rgb;
            InventoryCardPaint.diamond(c, x + 1, y + 1, shadow(fill));
            InventoryCardPaint.diamond(c, x, y, fill);
        }
    }

    /**
     * Footprint 5x5 at (x, y): hollow brass diamond for a passenger row, bright when hovered or selected. It carries no
     * colour and no alive/dead tint (leak guard). 乘客行的空心黄铜菱形，不带颜色、不区分生死（防泄露）。
     */
    public static void playerBullet(DrawContext c, int x, int y, boolean bright) {
        InventoryCardPaint.diamondOutline(c, x, y, bright ? InventoryCardPaint.BRASS_HI : InventoryCardPaint.BRASS_LO);
    }

    /**
     * Footprint (x, y)..(x+6, y+6): a 5x5 check (yes) or cross (no) over its (+1, +1) vanilla-shadow copy. The shape
     * carries the verdict; the colour only adds weight. 勾（是）或叉（否）加原版阴影；形状表达结论，颜色只作强调。
     */
    public static void stamp(DrawContext c, int x, int y, boolean check, int argb) {
        if (check) {
            InventoryCardPaint.check(c, x + 1, y + 1, shadow(argb));
            InventoryCardPaint.check(c, x, y, argb);
        } else {
            InventoryCardPaint.bitmap(c, x + 1, y + 1, shadow(argb), CROSS);
            InventoryCardPaint.bitmap(c, x, y, argb, CROSS);
        }
    }

    /**
     * Stamp colour of a received reading: READY_TEXT when assigned or alive, ALERT_TEXT when dead, MUTED when not
     * assigned. 已分配或存活为绿色，死亡为警示红，未分配为暗淡色。
     */
    public static int stampColor(int mode, boolean positive) {
        if (positive) {
            return InventoryCardPaint.READY_TEXT;
        }
        return mode == OpenTarotDivinationSelectorS2CPacket.MODE_SURVIVAL
                ? InventoryCardPaint.ALERT_TEXT : InventoryCardPaint.MUTED;
    }

    /**
     * Footprint 5x5 at (x, y): InventoryCardPaint.gem's recipe (bezel, 3x3 core, glint, shade) on {@link #gemCore},
     * so a role colour that is too dark on BODY never reads as an empty socket.
     * 与 InventoryCardPaint.gem 相同的配方，但核心色经 gemCore 处理，过暗的职业色不会看起来像空槽。
     */
    public static void roleGem(DrawContext c, int x, int y, int rgb, boolean bright) {
        int core = gemCore(rgb);
        int bezel = bright ? InventoryCardPaint.BRASS_HI : InventoryCardPaint.BRASS_LO;
        c.fill(x + 1, y, x + 4, y + 1, bezel);
        c.fill(x + 1, y + 4, x + 4, y + 5, bezel);
        c.fill(x, y + 1, x + 1, y + 4, bezel);
        c.fill(x + 4, y + 1, x + 5, y + 4, bezel);
        c.fill(x + 1, y + 1, x + 4, y + 4, core);
        c.fill(x + 1, y + 1, x + 2, y + 2, InventoryCardPaint.mix(core, 0xFFFFFFFF, 0.45));
        c.fill(x + 3, y + 3, x + 4, y + 4, InventoryCardPaint.mix(core, 0xFF000000, 0.35));
    }

    /**
     * The role colour with alpha forced opaque (Wathe and SparkWitch colours carry alpha 0x00, two NoellesRoles
     * colours 0xC0), lifted 35% toward white when its contrast against BODY is below 2.0:1.
     * 强制不透明（Wathe 与 SparkWitch 颜色 alpha 为 0x00，两个 NoellesRoles 颜色为 0xC0）；与 BODY 对比度低于 2.0:1 时
     * 向白色提亮 35%。
     */
    static int gemCore(int rgb) {
        int core = 0xFF000000 | rgb;
        return contrast(core, InventoryCardPaint.BODY) < GEM_MIN_CONTRAST
                ? InventoryCardPaint.mix(core, 0xFFFFFFFF, GEM_LIFT) : core;
    }

    /** WCAG contrast ratio of two colours (alpha ignored), 1.0 .. 21.0. */
    static double contrast(int a, int b) {
        double la = relativeLuminance(a);
        double lb = relativeLuminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** WCAG relative luminance of the RGB channels. */
    static double relativeLuminance(int rgb) {
        return 0.2126 * linear((rgb >> 16) & 0xFF) + 0.7152 * linear((rgb >> 8) & 0xFF) + 0.0722 * linear(rgb & 0xFF);
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    /** Vanilla text-shadow colour, alpha kept (same rule as the HUD pips). 原版文字阴影颜色，保留透明度。 */
    private static int shadow(int argb) {
        return (argb & 0xFF000000) | ((argb & 0xFCFCFC) >> 2);
    }
}
