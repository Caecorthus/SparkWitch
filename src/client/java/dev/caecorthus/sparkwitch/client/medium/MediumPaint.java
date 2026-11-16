package dev.caecorthus.sparkwitch.client.medium;

import dev.caecorthus.sparkfactionapi.client.api.card.InventoryCardPaint;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerPaint;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumBlessing;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumRules;
import net.minecraft.client.gui.DrawContext;

/**
 * Role-local tokens and fill primitives for the Medium's screens, HUD card and inventory drawer, in the Harpy Express
 * house style with the ghost-fire teal as the only accent (teal means "your pick / yours" and nothing else).
 * {@link InventoryCardPaint} is SparkFactionAPI's copy, shared with SparkTraits, and the Tarot classes belong to the
 * Tarot Reader, so the few recipes they lack (a taller answer button, a frame that can fade, the blessing icons, the
 * face tile) live here. Every primitive is fill-only: callers wrap them in {@link InventoryCardPaint#batch} and draw
 * text and textures after the batch.
 * 通灵师界面、HUD 卡片与背包抽屉的职业自有令牌与填充原语，沿用哈比特快风格，仅以鬼火青绿作为强调色（青绿只表示
 * 「你的选择 / 你的东西」）。{@link InventoryCardPaint} 位于 SparkFactionAPI、与 SparkTraits 共用，塔罗类归塔罗牌师所有，
 * 因此它们缺少的少量配方（更高的作答按钮、可渐隐的外框、加护图标、头像格）放在这里。所有原语只做填充：调用方用
 * {@link InventoryCardPaint#batch} 包裹，批次结束后再绘制文字与贴图。
 */
final class MediumPaint {
    /** The role colour, opaque. / 不透明的职业色。 */
    static final int TEAL = 0xFF000000 | MediumRules.COLOR;
    static final int TEAL_LO = InventoryCardPaint.mix(TEAL, 0xFF000000, 0.45);
    /**
     * Vanilla draws text with alpha below 4 fully opaque (TextRenderer.tweakTransparency), so a fading element stops
     * drawing under it.
     * 原版会把透明度低于 4 的文字按不透明绘制（TextRenderer.tweakTransparency），因此渐隐元素在此值以下停止绘制。
     */
    static final int MIN_ALPHA = 4;
    /** The frame reaches this far around its content box, like brassTooltip. / 外框超出内容框的宽度，与 brassTooltip 相同。 */
    static final int FRAME = 4;
    /** Blessing icons are 9x9. / 加护图标为 9x9。 */
    static final int ICON = 9;
    /** The face-tile frame around a 16 px face: 1 px edge + 1 px rim. / 16 像素头像外的格框：1 像素边 + 1 像素沿。 */
    static final int TILE = 20;

    private static final String[] WISP = {"..#..", ".##..", ".###.", "#####", "##.##", "##.##", ".###."};
    private static final String[] PLAY = {"#..", "##.", "###", "##.", "#.."};
    private static final String[] PLAY_LEFT = {"..#", ".##", "###", ".##", "..#"};
    // 9x9 blessing icons in the 1 px stroke family (owner-approved mock, 2026-11-13). / 1 像素描边风格的 9x9 加护图标。
    private static final String[] SPEED = {
            "#...#....", ".#...#...", "..#...#..", "...#...#.", "....#...#", "...#...#.", "..#...#..", ".#...#...",
            "#...#...."
    };
    private static final String[] SHIELD = {
            "#########", "#.......#", "#...#...#", "#..###..#", ".#..#..#.", ".#.....#.", "..#...#..", "...#.#...",
            "....#...."
    };
    private static final String[] EYE = {
            ".........", "..#####..", ".#.....#.", "#...#...#", "#..###..#", "#...#...#", ".#.....#.", "..#####..",
            "........."
    };
    private static final String[] SMILE = {
            "..#####..", ".#.....#.", "#.......#", "#.#...#.#", "#.......#", "#.#...#.#", "#..###..#", ".#.....#.",
            "..#####.."
    };

    private MediumPaint() {
    }

    /** Footprint 5x7 at (x, y): the ghost-fire wisp. / 5x7 的鬼火图标。 */
    static void wisp(DrawContext c, int x, int y, int col) {
        InventoryCardPaint.bitmap(c, x, y, col, WISP);
    }

    /** Footprint 9x9 at (x, y). / 9x9 的加护图标。 */
    static void blessingIcon(DrawContext c, int x, int y, MediumBlessing blessing, int col) {
        InventoryCardPaint.bitmap(c, x, y, col, iconRows(blessing));
    }

    /**
     * Footprint (9 * scale + scale) square at (x, y): the icon at an integer scale over its vanilla text-shadow copy
     * at (+scale, +scale), for icons drawn over the world (HUD card, corner chips).
     * 以整数倍缩放绘制图标，并在 (+scale, +scale) 处先绘制原版文字阴影色的副本，用于世界画面上的图标（HUD 卡片、角落图标）。
     */
    static void blessingIconShadowed(DrawContext c, int x, int y, MediumBlessing blessing, int col, int scale) {
        String[] rows = iconRows(blessing);
        scaledBitmap(c, x + scale, y + scale, textShadow(col), scale, rows);
        scaledBitmap(c, x, y, col, scale, rows);
    }

    private static String[] iconRows(MediumBlessing blessing) {
        return switch (blessing) {
            case SPEED -> SPEED;
            case SHIELD -> SHIELD;
            case CORPSE_SIGHT -> EYE;
            case SANITY -> SMILE;
        };
    }

    private static void scaledBitmap(DrawContext c, int x, int y, int col, int scale, String[] rows) {
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            int k = 0;
            while (k < row.length()) {
                if (row.charAt(k) != '#') {
                    k++;
                    continue;
                }
                int start = k;
                while (k < row.length() && row.charAt(k) == '#') {
                    k++;
                }
                c.fill(x + start * scale, y + r * scale, x + k * scale, y + (r + 1) * scale, col);
            }
        }
    }

    /**
     * Footprint (x, y)..(x+w, y+h): the raised price-tag pill at any height, without the play icon. Hover warms the
     * body and coins the rim; the answer already sent for this window keeps a teal rim on the selected body.
     * 任意高度的凸起价签按钮，不带播放图标。悬停时底色加深、边缘变金币色；本窗口已提交的回答保持青绿边缘与选中底色。
     */
    static void voteButton(DrawContext c, int x, int y, int w, int h, boolean hovered, boolean chosen) {
        int top = chosen ? TEAL : hovered ? InventoryCardPaint.COIN : InventoryCardPaint.BRASS_HI;
        int bottom = chosen ? TEAL_LO : InventoryCardPaint.BRASS_LO;
        int body = chosen ? TarotLedgerPaint.SELECT
                : hovered ? TarotLedgerPaint.BUTTON_HOVER : InventoryCardPaint.TIP_BG;
        raised(c, x, y, w, h, body, top, bottom);
    }

    static int voteLabelColor(boolean hovered, boolean chosen) {
        return chosen ? InventoryCardPaint.TEXT_HI : TarotLedgerPaint.tagButtonLabelColor(true, hovered);
    }

    /**
     * Footprint (x, y)..(x+w, y+h): InventoryCardPaint.pill's sunken slot at any height (WELL, WELL_SHADE top row,
     * RIM_HI bottom lip), the "cannot be chosen" grammar.
     * 任意高度的凹槽（WELL、顶行 WELL_SHADE、底沿 RIM_HI），表示「不可选」。
     */
    static void lockedCard(DrawContext c, int x, int y, int w, int h) {
        InventoryCardPaint.roundedFill(c, x, y, w, h, InventoryCardPaint.WELL);
        c.fill(x + 1, y, x + w - 1, y + 1, InventoryCardPaint.WELL_SHADE);
        c.fill(x + 1, y + h - 1, x + w - 1, y + h, InventoryCardPaint.RIM_HI);
    }

    /** Footprint (x, y)..(x+w, y+11): the raised 11 px price chip. / 11 像素高的凸起价签。 */
    static void priceChip(DrawContext c, int x, int y, int w) {
        raised(c, x, y, w, 11, InventoryCardPaint.TIP_BG, InventoryCardPaint.BRASS_HI, InventoryCardPaint.BRASS_LO);
    }

    /**
     * Footprint (x, y)..(x+9, y+11): a page arrow; enabled = raised tag with a COIN chevron, disabled = sunken slot
     * with a BRASS_LO chevron.
     * 翻页箭头：可用时为凸起价签加金币色箭头，不可用时为凹槽加暗黄铜箭头。
     */
    static void pageArrow(DrawContext c, int x, int y, boolean left, boolean enabled) {
        if (enabled) {
            raised(c, x, y, 9, 11, InventoryCardPaint.TIP_BG, InventoryCardPaint.BRASS_HI, InventoryCardPaint.BRASS_LO);
        } else {
            lockedCard(c, x, y, 9, 11);
        }
        InventoryCardPaint.bitmap(c, x + 3, y + 3, enabled ? InventoryCardPaint.COIN : InventoryCardPaint.BRASS_LO,
                left ? PLAY_LEFT : PLAY);
    }

    /**
     * Footprint (x, y)..(x+20, y+20), plus (x-1, y-1)..(x+21, y+21) when selected: the face-tile frame (EDGE base and
     * a 1 px rim) drawn before the face. Normal BRASS rim, hover COIN, selected TEAL with a 1 px teal halo, bound a
     * dull mahogany rim. No state ever depends on whether the player is alive (leak guard).
     * 头像格外框（EDGE 底与 1 像素边沿），在头像之前绘制。常态黄铜边沿，悬停金币色，选中青绿并带 1 像素青绿外环，已招魂为
     * 暗红木边沿。任何状态都不取决于玩家是否存活（防泄露）。
     */
    static void faceTileFrame(DrawContext c, int x, int y, boolean hovered, boolean selected, boolean bound) {
        InventoryCardPaint.roundedFill(c, x, y, TILE, TILE, InventoryCardPaint.EDGE);
        int top = bound ? InventoryCardPaint.RIM_HI : selected ? TEAL : hovered ? InventoryCardPaint.COIN
                : InventoryCardPaint.BRASS;
        int bottom = bound ? InventoryCardPaint.RIM_LO : selected ? TEAL_LO : hovered ? InventoryCardPaint.BRASS
                : InventoryCardPaint.BRASS_LO;
        c.fill(x + 1, y + 1, x + TILE - 1, y + 2, top);
        c.fill(x + 1, y + TILE - 2, x + TILE - 1, y + TILE - 1, bottom);
        c.fillGradient(x + 1, y + 2, x + 2, y + TILE - 2, top, bottom);
        c.fillGradient(x + TILE - 2, y + 2, x + TILE - 1, y + TILE - 2, top, bottom);
        if (selected) {
            InventoryCardPaint.roundedOutline(c, x - 1, y - 1, TILE + 2, TILE + 2, TEAL);
        }
    }

    /**
     * Footprint (x+2, y+2)..(x+19, y+19), drawn after the face: a bound face is dimmed and carries the teal wisp badge
     * on a TIP_BG patch; a hovered free face gets the HOVER wash.
     * 头像之后绘制：已招魂的头像压暗，并在 TIP_BG 底块上带青绿鬼火角标；悬停的可选头像加 HOVER 底色。
     */
    static void faceTileOverlay(DrawContext c, int x, int y, boolean hovered, boolean bound) {
        if (bound) {
            c.fill(x + 2, y + 2, x + TILE - 2, y + TILE - 2, 0x99000000);
            c.fill(x + 12, y + 10, x + 19, y + 19, InventoryCardPaint.TIP_BG);
            wisp(c, x + 13, y + 11, TEAL);
        } else if (hovered) {
            c.fill(x + 2, y + 2, x + TILE - 2, y + TILE - 2, InventoryCardPaint.HOVER);
        }
    }

    private static void raised(DrawContext c, int x, int y, int w, int h, int body, int top, int bottom) {
        InventoryCardPaint.roundedFill(c, x, y, w, h, body);
        c.fill(x + 1, y, x + w - 1, y + 1, top);
        c.fill(x + 1, y + h - 1, x + w - 1, y + h, bottom);
        c.fillGradient(x, y + 1, x + 1, y + h - 1, top, bottom);
        c.fillGradient(x + w - 1, y + 1, x + w, y + h - 1, top, bottom);
    }

    /**
     * {@code InventoryCardPaint.brassTooltip}'s pixels around the content box (x, y, w, h), with every colour scaled
     * by {@code alpha}. No two fills overlap, so each pixel blends exactly once and the frame fades evenly (the same
     * technique as the Tarot reading slip).
     * 围绕内容框 (x, y, w, h) 的 {@code InventoryCardPaint.brassTooltip} 像素，每种颜色都按 {@code alpha} 缩放。各填充互不
     * 重叠，每个像素只混合一次，外框因此均匀渐隐（与塔罗结果条相同的做法）。
     */
    static void fadeFrame(DrawContext c, int x, int y, int w, int h, int alpha) {
        int back = withAlpha(InventoryCardPaint.TIP_BG, alpha);
        int top = withAlpha(InventoryCardPaint.BRASS_HI, alpha);
        int bottom = withAlpha(InventoryCardPaint.BRASS_LO, alpha);
        c.fill(x - 3, y - 4, x + w + 3, y - 3, back);
        c.fill(x - 3, y + h + 3, x + w + 3, y + h + 4, back);
        c.fill(x - 4, y - 3, x - 3, y + h + 3, back);
        c.fill(x + w + 3, y - 3, x + w + 4, y + h + 3, back);
        c.fill(x - 3, y - 3, x + w + 3, y - 2, top);
        c.fill(x - 3, y + h + 2, x + w + 3, y + h + 3, bottom);
        c.fillGradient(x - 3, y - 2, x - 2, y + h + 2, top, bottom);
        c.fillGradient(x + w + 2, y - 2, x + w + 3, y + h + 2, top, bottom);
        c.fill(x - 2, y - 2, x + w + 2, y + h + 2, back);
    }

    /** Scales a colour's own alpha by {@code alpha} / 255. / 按 {@code alpha} / 255 缩放颜色自身的透明度。 */
    static int withAlpha(int argb, int alpha) {
        return (((argb >>> 24) * alpha / 0xFF) << 24) | (argb & 0xFFFFFF);
    }

    /** Vanilla text-shadow colour, alpha kept. / 原版文字阴影颜色，保留透明度。 */
    static int textShadow(int argb) {
        return (argb & 0xFF000000) | ((argb & 0xFCFCFC) >> 2);
    }
}
