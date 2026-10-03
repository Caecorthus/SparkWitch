package dev.caecorthus.sparkwitch.client.blackraven;

import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenLedgerLayout.Rect;
import dev.caecorthus.sparkwitch.client.gui.InventoryCardPaint;
import net.minecraft.client.gui.DrawContext;

/**
 * Role-local "raven noir" tokens and fill primitives for the Black Raven Perception Ledger: the Harpy Express panel
 * grammar (bevelled frame, title band, engraved rules, gems, pills) recoloured to obsidian, silver-violet and a
 * blue-green glint. Shapes reuse {@link InventoryCardPaint} (which must stay byte-identical with SparkTraits, so
 * nothing is added there); sizes belong to {@link BlackRavenLedgerLayout}. Every primitive is fill-only: none batches
 * and none draws text, so callers wrap them in {@link InventoryCardPaint#batch} and draw text after the batch.
 * 黑羽鸦感知册的职业自有“鸦羽暗夜”令牌与填充原语：沿用哈比特快面板语法（斜面外框、标题带、刻线、宝石、状态牌），
 * 配色改为黑曜石、银紫与蓝绿光泽。图形复用 {@link InventoryCardPaint}（须与 SparkTraits 逐字节一致，因此不在其中
 * 添加任何内容）；尺寸归 {@link BlackRavenLedgerLayout} 所有。所有原语只做填充，既不自行分批也不绘制文字；调用方用
 * {@link InventoryCardPaint#batch} 包裹，批次结束后再绘制文字。
 */
public final class BlackRavenLedgerPaint {
    // ---- tokens (ARGB) ----
    /** Light scrim: the round keeps running behind the ledger. 浅遮罩：感知册打开时对局仍在进行。 */
    public static final int SCRIM = 0xA806040A;
    public static final int SHADOW = 0x66000000, EDGE = 0xFF050307;
    public static final int RIM_HI = 0xFF3E3449, RIM_LO = 0xFF17121D, RIM_MITER = 0xFF2A2333;
    /** Same RGB as {@code BlackRavenRules.COLOR}: the dark end of the silver-violet metal. 与 BlackRavenRules.COLOR 同色。 */
    public static final int SHEEN_LO = 0xFF51445F;
    public static final int SHEEN_MID = 0xFF7A6B8E;
    /** Feather violet: the bright end of the metal. 羽毛紫：金属高光端。 */
    public static final int SHEEN = 0xFF9D8BC2;
    public static final int SHEEN_HI = 0xFFC9BEE0;
    /** Raven blue-green iridescence: "active / selected / ready to pick". 鸦羽蓝绿光泽：表示“当前/选中/可选”。 */
    public static final int GLINT = 0xFF7FD1C2;
    public static final int BODY = 0xFF141019, BAND = 0xFF1F1829, SELECT = 0xFF2D2340;
    public static final int WELL = 0xFF0D0A11, WELL_SHADE = 0xFF070509, TIP_BG = 0xFF100C15;
    public static final int BUTTON_HOVER = 0xFF241C30;
    public static final int HOVER = 0x18B9A6D6, ETCH = 0x803E3449, RULE = 0x5051445F;
    public static final int TEXT = 0xFFE7E0EF, TEXT_HI = 0xFFFFFFFF, MUTED = 0xFF9E94AD, FAINT = 0xFF7E7590;
    /** Unselectable roles: dim, but still 3:1 on BODY. 不可选职业：暗淡，但在 BODY 上仍有 3:1 的对比度。 */
    public static final int GREYED = 0xFF685F76;
    public static final int READY = 0xFFA9E98C, COOL = 0xFFE3CC94, ALERT = 0xFFF2838B;
    public static final int FEATHER_SHADE = 0xFF37303F;

    /** A gem core must reach this WCAG contrast on BODY, else it is lifted. 宝石核心在 BODY 上须达到的对比度。 */
    static final double GEM_MIN_CONTRAST = 2.0;
    static final double GEM_LIFT = 0.35;

    /** 7 x 9 raven feather: vane down to the left, quill at the bottom. 7 × 9 鸦羽：羽片向左下，底部为羽管。 */
    static final String[] FEATHER = {
            "....###", "...####", "..####.", ".####..", ".###...", ".##....", ".#.....", "#......", "#......"
    };
    static final String[] ARROW_LEFT = {"..#", ".##", "###", ".##", "..#"};

    /** Footer status kinds; each has its own glyph so it survives grayscale. 页脚状态种类；各有独立图形，灰度下仍可辨认。 */
    public enum PillKind {
        /** Raised tag: a live mask session can pick now. 凸起：面具会话有效，可立即选择。 */
        READY,
        COOLDOWN,
        LOCKED,
        /** Read-only: the ledger was not opened through the mask. 只读：未通过假面打开。 */
        IDLE,
        EXPIRED
    }

    private BlackRavenLedgerPaint() {
    }

    // ---- frame ----
    /**
     * Footprint (x, y)..(x+w+1, y+h+1): the house panel recipe in noir tokens. L-shaped drop shadow, 1 px EDGE outline
     * (corners cut), BODY, 1 px bevel (RIM_HI top/left, RIM_LO bottom/right, mitred), then a 1 px metal ring running
     * SHEEN to SHEEN_LO downwards. Content starts at +3.
     * 哈比特快面板配方的暗夜配色：L 形投影、1 像素 EDGE 描边（切角）、BODY、1 像素斜面、再一圈自上而下由 SHEEN 渐变到
     * SHEEN_LO 的金属线。内容自 +3 起。
     */
    public static void frame(DrawContext c, int x, int y, int w, int h) {
        c.fill(x + w, y + 2, x + w + 1, y + h, SHADOW);
        c.fill(x + 2, y + h, x + w, y + h + 1, SHADOW);
        InventoryCardPaint.roundedOutline(c, x, y, w, h, EDGE);
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, BODY);
        c.fill(x + 1, y + 1, x + w - 1, y + 2, RIM_HI);
        c.fill(x + 1, y + 2, x + 2, y + h - 1, RIM_HI);
        c.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, RIM_LO);
        c.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, RIM_LO);
        c.fill(x + w - 2, y + 1, x + w - 1, y + 2, RIM_MITER);
        c.fill(x + 1, y + h - 2, x + 2, y + h - 1, RIM_MITER);
        c.fill(x + 2, y + 2, x + w - 2, y + 3, SHEEN);
        c.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, SHEEN_LO);
        c.fillGradient(x + 2, y + 3, x + 3, y + h - 3, SHEEN, SHEEN_LO);
        c.fillGradient(x + w - 3, y + 3, x + w - 2, y + h - 3, SHEEN, SHEEN_LO);
    }

    /** Footprint (x+3, y+3)..(x+w-3, y+21): BAND, then a SHEEN_LO rule and an EDGE rule. 标题带及其下两条刻线。 */
    public static void titleBand(DrawContext c, int x, int y, int w) {
        c.fill(x + 3, y + BlackRavenLedgerLayout.BAND_TOP, x + w - 3, y + BlackRavenLedgerLayout.BAND_BOTTOM, BAND);
        engraved(c, x + 3, x + w - 3, y + BlackRavenLedgerLayout.BAND_BOTTOM);
    }

    /** Footprint (x1, y)..(x2, y+2): SHEEN_LO over EDGE. 刻线：SHEEN_LO 在上、EDGE 在下。 */
    public static void engraved(DrawContext c, int x1, int x2, int y) {
        c.fill(x1, y, x2, y + 1, SHEEN_LO);
        c.fill(x1, y + 1, x2, y + 2, EDGE);
    }

    /** Footprint (x1, y)..(x2, y+2): EDGE over ETCH (the footer rule). 页脚刻线。 */
    public static void etched(DrawContext c, int x1, int x2, int y) {
        c.fill(x1, y, x2, y + 1, EDGE);
        c.fill(x1, y + 1, x2, y + 2, ETCH);
    }

    /** Footprint 8x10 at (x, y): the title feather over its (+1, +1) shade. 标题羽毛及其 (+1, +1) 阴影。 */
    public static void titleFeather(DrawContext c, int x, int y) {
        feather(c, x + 1, y + 1, FEATHER_SHADE, 1);
        feather(c, x, y, SHEEN_HI, 1);
    }

    /** Footprint 7x9 times {@code scale} at (x, y). 羽毛图标，按 scale 放大。 */
    public static void feather(DrawContext c, int x, int y, int argb, int scale) {
        int k = Math.max(1, scale);
        for (int row = 0; row < FEATHER.length; row++) {
            String line = FEATHER[row];
            int i = 0;
            while (i < line.length()) {
                if (line.charAt(i) != '#') {
                    i++;
                    continue;
                }
                int start = i;
                while (i < line.length() && line.charAt(i) == '#') {
                    i++;
                }
                c.fill(x + start * k, y + row * k, x + i * k, y + (row + 1) * k, argb);
            }
        }
    }

    /** Footprint 1 x CHIP_DIVIDER_H at (x, y): the chip's divider. 徽章分隔线。 */
    public static void chipDivider(DrawContext c, int x, int y) {
        c.fill(x, y, x + 1, y + BlackRavenLedgerLayout.CHIP_DIVIDER_H, SHEEN_LO);
    }

    // ---- tabs ----
    /**
     * Footprint = rect, plus 2 px below it for the active tab. Active: SELECT with a SHEEN top line, metal sides, and
     * a GLINT bar that replaces the row rule under it; inactive: bare, hover washed.
     * 激活：SELECT 填充、顶部 SHEEN 线、两侧金属线，下方以 GLINT 条覆盖页签行刻线；未激活：无底色，悬停时加淡色。
     */
    public static void tab(DrawContext c, Rect r, boolean active, boolean hovered) {
        if (active) {
            c.fill(r.x(), r.y(), r.right(), r.bottom(), SELECT);
            c.fill(r.x(), r.y(), r.right(), r.y() + 1, SHEEN);
            c.fillGradient(r.x(), r.y() + 1, r.x() + 1, r.bottom(), SHEEN, SHEEN_LO);
            c.fillGradient(r.right() - 1, r.y() + 1, r.right(), r.bottom(), SHEEN, SHEEN_LO);
            c.fill(r.x() + 1, r.bottom(), r.right() - 1, r.bottom() + 2, GLINT);
        } else if (hovered) {
            c.fill(r.x(), r.y(), r.right(), r.bottom(), HOVER);
        }
    }

    /** Footprint = rect: a sunken keycap (WELL, WELL_SHADE top row, RIM_HI bottom lip). 凹陷键帽。 */
    public static void keycap(DrawContext c, Rect r) {
        sunken(c, r);
    }

    private static void sunken(DrawContext c, Rect r) {
        InventoryCardPaint.roundedFill(c, r.x(), r.y(), r.width(), r.height(), WELL);
        c.fill(r.x() + 1, r.y(), r.right() - 1, r.y() + 1, WELL_SHADE);
        c.fill(r.x() + 1, r.bottom() - 1, r.right() - 1, r.bottom(), RIM_HI);
    }

    private static void raised(DrawContext c, Rect r, int fill, int top) {
        InventoryCardPaint.roundedFill(c, r.x(), r.y(), r.width(), r.height(), fill);
        c.fill(r.x() + 1, r.y(), r.right() - 1, r.y() + 1, top);
        c.fill(r.x() + 1, r.bottom() - 1, r.right() - 1, r.bottom(), SHEEN_LO);
        c.fillGradient(r.x(), r.y() + 1, r.x() + 1, r.bottom() - 1, top, SHEEN_LO);
        c.fillGradient(r.right() - 1, r.y() + 1, r.right(), r.bottom() - 1, top, SHEEN_LO);
    }

    // ---- Tab A rows ----
    /** Footprint = the cell minus its last row (the ruled line): the hover wash. 悬停淡色，不覆盖行下横线。 */
    public static void rowHover(DrawContext c, Rect r) {
        c.fill(r.x(), r.y(), r.right(), r.bottom() - 1, HOVER);
    }

    /** Footprint (cell.x + ROW_RULE_X, cell.bottom - 1)..(cell.right, cell.bottom). 行下横线。 */
    public static void rowRule(DrawContext c, Rect r) {
        c.fill(r.x() + BlackRavenLedgerLayout.ROW_RULE_X, r.bottom() - 1, r.right(), r.bottom(), RULE);
    }

    /** Footprint FACE_FRAME_SIZE square at (x, y), corners cut: the portrait frame. 头像外框，切角。 */
    public static void faceFrame(DrawContext c, int x, int y, boolean bright) {
        int size = BlackRavenLedgerLayout.FACE_FRAME_SIZE;
        InventoryCardPaint.roundedOutline(c, x, y, size, size, bright ? SHEEN : SHEEN_LO);
    }

    // ---- gems and marks ----
    /**
     * Footprint 5x5 at (x, y): the house gem (bezel with cut corners, 3x3 core, glint, shade) on {@link #gemCore}.
     * 5×5 宝石：切角镶边、3×3 核心、左上高光、右下暗角；核心色经 gemCore 处理。
     */
    public static void roleGem(DrawContext c, int x, int y, int rgb, boolean bright) {
        int core = gemCore(rgb);
        int bezel = bright ? SHEEN : SHEEN_LO;
        c.fill(x + 1, y, x + 4, y + 1, bezel);
        c.fill(x + 1, y + 4, x + 4, y + 5, bezel);
        c.fill(x, y + 1, x + 1, y + 4, bezel);
        c.fill(x + 4, y + 1, x + 5, y + 4, bezel);
        c.fill(x + 1, y + 1, x + 4, y + 4, core);
        c.fill(x + 1, y + 1, x + 2, y + 2, InventoryCardPaint.mix(core, 0xFFFFFFFF, 0.45));
        c.fill(x + 3, y + 3, x + 4, y + 4, InventoryCardPaint.mix(core, 0xFF000000, 0.35));
    }

    /** Footprint 5x5 at (x, y): the empty socket of an unselectable role. 不可选职业的空宝石座。 */
    public static void emptyGem(DrawContext c, int x, int y) {
        InventoryCardPaint.diamondOutline(c, x, y, GREYED);
    }

    /**
     * Footprint (x, y)..(x+6, y+6): a section mark. Live = a GLINT diamond over its (+1, +1) shade; otherwise a FAINT
     * outline. 分区标记：可选分区为 GLINT 实心菱形加阴影，其余为 FAINT 空心菱形。
     */
    public static void sectionMark(DrawContext c, int x, int y, boolean live) {
        if (live) {
            InventoryCardPaint.diamond(c, x + 1, y + 1, shade(GLINT));
            InventoryCardPaint.diamond(c, x, y, GLINT);
        } else {
            InventoryCardPaint.diamondOutline(c, x, y, FAINT);
        }
    }

    /** Footprint (x1, y)..(x2, y+2): a section's engraved rule, dimmed for unselectable sections. 分区刻线。 */
    public static void sectionRule(DrawContext c, int x1, int x2, int y, boolean live) {
        c.fill(x1, y, x2, y + 1, live ? SHEEN_LO : InventoryCardPaint.mix(SHEEN_LO, BODY, 0.5));
        c.fill(x1, y + 1, x2, y + 2, EDGE);
    }

    /**
     * Footprint = rect: current = SELECT with a 2 px GLINT bar on the left (the identity being worn), else hot = the
     * hover wash. 当前身份为 SELECT 底加左侧 2 像素 GLINT 条；可点击且悬停时为淡色。
     */
    public static void cellState(DrawContext c, Rect r, boolean current, boolean hot) {
        if (current) {
            c.fill(r.x(), r.y(), r.right(), r.bottom(), SELECT);
            c.fill(r.x(), r.y(), r.x() + 2, r.bottom(), GLINT);
        } else if (hot) {
            c.fill(r.x(), r.y(), r.right(), r.bottom(), HOVER);
        }
    }

    // ---- footer ----
    /**
     * Footprint = rect (PILL_H tall): READY is a raised tag, every other kind a sunken groove; then the kind's glyph
     * at ({@code iconX}, {@code iconY}).
     * 状态牌：READY 为凸起价签，其余为凹槽；随后在 (iconX, iconY) 绘制该状态的图形。
     */
    public static void pill(DrawContext c, Rect r, PillKind kind, int iconX, int iconY) {
        if (kind == PillKind.READY) {
            raised(c, r, TIP_BG, SHEEN);
        } else {
            sunken(c, r);
        }
        switch (kind) {
            case READY -> InventoryCardPaint.check(c, iconX, iconY, READY);
            case COOLDOWN -> InventoryCardPaint.hourglass(c, iconX, iconY, SHEEN);
            case LOCKED -> InventoryCardPaint.padlock(c, iconX, iconY - 1, MUTED, WELL);
            case IDLE -> InventoryCardPaint.diamondOutline(c, iconX, iconY, FAINT);
            case EXPIRED -> InventoryCardPaint.padlock(c, iconX, iconY - 1, ALERT, WELL);
        }
    }

    public static int pillTextColor(PillKind kind) {
        return switch (kind) {
            case READY -> READY;
            case COOLDOWN -> COOL;
            case LOCKED, IDLE -> MUTED;
            case EXPIRED -> ALERT;
        };
    }

    /**
     * Footprint = rect (GAUGE_H tall): a 3 px WELL track over a 1 px RIM_HI lip, {@code fill} px lit from the left.
     * 计量条：3 像素 WELL 轨道加 1 像素 RIM_HI 底边，左起 fill 像素点亮。
     */
    public static void gauge(DrawContext c, Rect r, int fill) {
        if (r.width() <= 0) {
            return;
        }
        int trackBottom = r.bottom() - 1;
        c.fill(r.x(), r.y(), r.right(), trackBottom, WELL);
        c.fill(r.x(), trackBottom, r.right(), r.bottom(), RIM_HI);
        int lit = Math.min(fill, r.width());
        if (lit > 0) {
            c.fill(r.x(), r.y(), r.x() + lit, trackBottom, SHEEN);
            c.fill(r.x(), r.y(), r.x() + lit, r.y() + 1, InventoryCardPaint.mix(SHEEN, 0xFFFFFFFF, 0.35));
            c.fill(r.x(), trackBottom - 1, r.x() + lit, trackBottom, InventoryCardPaint.mix(SHEEN, 0xFF000000, 0.35));
        }
    }

    /**
     * Footprint = rect: the revert button with its feather at (x+BUTTON_PAD_L, y+BUTTON_ICON_Y). Enabled = a raised
     * tag (hover warms the body and lights the top edge in GLINT); disabled = a sunken slot, hover ignored.
     * 恢复按钮及其羽毛图标。可用时为凸起价签（悬停加深底色、顶边变为 GLINT）；不可用时为凹槽，忽略悬停。
     */
    public static void button(DrawContext c, Rect r, boolean enabled, boolean hovered) {
        int iconX = r.x() + BlackRavenLedgerLayout.BUTTON_PAD_L;
        int iconY = r.y() + BlackRavenLedgerLayout.BUTTON_ICON_Y;
        if (!enabled) {
            sunken(c, r);
            feather(c, iconX, iconY, SHEEN_LO, 1);
            return;
        }
        raised(c, r, hovered ? BUTTON_HOVER : TIP_BG, hovered ? GLINT : SHEEN);
        feather(c, iconX, iconY, hovered ? TEXT_HI : SHEEN_HI, 1);
    }

    public static int buttonLabelColor(boolean enabled, boolean hovered) {
        if (!enabled) {
            return FAINT;
        }
        return hovered ? TEXT_HI : SHEEN_HI;
    }

    /**
     * Footprint = rect: a pager arrow box; enabled hover washes it, disabled draws GREYED and ignores hover.
     * 翻页箭头框；可用时悬停加淡色，不可用时为 GREYED 且忽略悬停。
     */
    public static void arrow(DrawContext c, Rect r, boolean left, boolean enabled, boolean hovered) {
        InventoryCardPaint.roundedOutline(c, r.x(), r.y(), r.width(), r.height(), enabled ? SHEEN_LO : RIM_HI);
        boolean hot = enabled && hovered;
        if (hot) {
            c.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.bottom() - 1, HOVER);
        }
        int color = !enabled ? GREYED : hot ? TEXT_HI : MUTED;
        int gx = r.x() + (r.width() - 3) / 2;
        int gy = r.y() + (r.height() - 5) / 2;
        if (left) {
            InventoryCardPaint.bitmap(c, gx, gy, color, ARROW_LEFT);
        } else {
            InventoryCardPaint.play(c, gx, gy, color);
        }
    }

    // ---- scrolling ----
    /**
     * Footprint = track: an EDGE line down the middle, then the rod over it; hot = hovered or dragged.
     * 轨道中线为 EDGE，其上绘制滑杆；hot 表示悬停或拖动中。
     */
    public static void rod(DrawContext c, Rect track, Rect thumb, boolean hot) {
        c.fill(track.x() + 1, track.y(), track.right() - 1, track.bottom(), EDGE);
        if (thumb.width() <= 0) {
            return;
        }
        int body = hot ? SHEEN_MID : SHEEN_LO;
        int light = hot ? SHEEN : SHEEN_MID;
        c.fill(thumb.x(), thumb.y(), thumb.right(), thumb.bottom(), body);
        c.fill(thumb.x(), thumb.y(), thumb.x() + 1, thumb.bottom(), light);
        c.fill(thumb.x(), thumb.y(), thumb.right(), thumb.y() + 1, light);
        c.fill(thumb.right() - 1, thumb.y() + 1, thumb.right(), thumb.bottom(), RIM_HI);
    }

    /** Footprint (x1, y)..(x2, y+FADE_H): BODY fading to transparent downwards. 自上而下由 BODY 渐隐。 */
    public static void fadeTop(DrawContext c, int x1, int y, int x2) {
        c.fillGradient(x1, y, x2, y + BlackRavenLedgerLayout.FADE_H, BODY, BODY & 0xFFFFFF);
    }

    /** Footprint (x1, y2-FADE_H)..(x2, y2): transparent fading to BODY. 自上而下渐显为 BODY。 */
    public static void fadeBottom(DrawContext c, int x1, int y2, int x2) {
        c.fillGradient(x1, y2 - BlackRavenLedgerLayout.FADE_H, x2, y2, BODY & 0xFFFFFF, BODY);
    }

    /** Vanilla tooltip geometry in ledger colours; (x, y, w, h) is the content box. 原版提示框几何，感知册配色。 */
    public static void tooltip(DrawContext c, int x, int y, int w, int h) {
        c.fill(x - 3, y - 4, x + w + 3, y - 3, TIP_BG);
        c.fill(x - 3, y + h + 3, x + w + 3, y + h + 4, TIP_BG);
        c.fill(x - 3, y - 3, x + w + 3, y + h + 3, TIP_BG);
        c.fill(x - 4, y - 3, x - 3, y + h + 3, TIP_BG);
        c.fill(x + w + 3, y - 3, x + w + 4, y + h + 3, TIP_BG);
        c.fillGradient(x - 3, y - 2, x - 2, y + h + 2, SHEEN, SHEEN_LO);
        c.fillGradient(x + w + 2, y - 2, x + w + 3, y + h + 2, SHEEN, SHEEN_LO);
        c.fill(x - 3, y - 3, x + w + 3, y - 2, SHEEN);
        c.fill(x - 3, y + h + 2, x + w + 3, y + h + 3, SHEEN_LO);
    }

    // ---- colour ----
    /**
     * A role colour as a gem core: alpha forced opaque (Wathe and SparkWitch colours carry alpha 0x00, two
     * NoellesRoles colours 0xC0), lifted {@link #GEM_LIFT} toward white when its WCAG contrast on BODY is below
     * {@link #GEM_MIN_CONTRAST}, so a dark role colour never reads as an empty socket. Same rule as the tarot gem.
     * 作为宝石核心的职业色：强制不透明（Wathe 与 SparkWitch 颜色 alpha 为 0x00，两个 NoellesRoles 颜色为 0xC0）；与 BODY 的
     * 对比度低于 2.0:1 时向白色提亮 35%，过暗的职业色不会看起来像空槽。与塔罗宝石同一规则。
     */
    public static int gemCore(int rgb) {
        int core = 0xFF000000 | rgb;
        return contrast(core, BODY) < GEM_MIN_CONTRAST ? InventoryCardPaint.mix(core, 0xFFFFFFFF, GEM_LIFT) : core;
    }

    /** WCAG contrast ratio of two colours (alpha ignored), 1.0 .. 21.0. 两色的 WCAG 对比度。 */
    static double contrast(int a, int b) {
        double la = relativeLuminance(a);
        double lb = relativeLuminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** WCAG relative luminance of the RGB channels. RGB 的 WCAG 相对亮度。 */
    static double relativeLuminance(int rgb) {
        return 0.2126 * linear((rgb >> 16) & 0xFF) + 0.7152 * linear((rgb >> 8) & 0xFF) + 0.0722 * linear(rgb & 0xFF);
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    /** Vanilla text-shadow colour, alpha kept. 原版文字阴影颜色，保留透明度。 */
    private static int shade(int argb) {
        return (argb & 0xFF000000) | ((argb & 0xFCFCFC) >> 2);
    }
}
