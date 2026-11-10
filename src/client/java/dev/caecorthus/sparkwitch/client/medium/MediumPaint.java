package dev.caecorthus.sparkwitch.client.medium;

import dev.caecorthus.sparkwitch.client.gui.InventoryCardPaint;
import dev.caecorthus.sparkwitch.client.tarot.TarotLedgerPaint;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumRules;
import net.minecraft.client.gui.DrawContext;

/**
 * Role-local tokens and fill primitives for the Medium's vote screen and result card, in the Harpy Express house
 * style with the ghost-fire teal as the only accent. {@link InventoryCardPaint} must stay byte-identical with
 * SparkTraits and the Tarot classes belong to the Tarot Reader, so the few recipes they lack (a taller answer button,
 * a frame that can fade) live here. Every primitive is fill-only: callers wrap them in
 * {@link InventoryCardPaint#batch} and draw text after the batch.
 * 通灵师投票界面与结果卡片的职业自有令牌与填充原语，沿用哈比特快风格，仅以鬼火青绿作为强调色。
 * {@link InventoryCardPaint} 须与 SparkTraits 逐字节一致，塔罗类归塔罗牌师所有，因此它们缺少的少量配方（更高的作答按钮、
 * 可渐隐的外框）放在这里。所有原语只做填充：调用方用 {@link InventoryCardPaint#batch} 包裹，批次结束后再绘制文字。
 */
final class MediumPaint {
    /** The role colour, opaque. / 不透明的职业色。 */
    static final int TEAL = 0xFF000000 | MediumRules.COLOR;
    static final int TEAL_LO = InventoryCardPaint.mix(TEAL, 0xFF000000, 0.45);
    static final int WISP_W = 5;
    static final int WISP_H = 7;
    /**
     * Vanilla draws text with alpha below 4 fully opaque (TextRenderer.tweakTransparency), so a fading element stops
     * drawing under it.
     * 原版会把透明度低于 4 的文字按不透明绘制（TextRenderer.tweakTransparency），因此渐隐元素在此值以下停止绘制。
     */
    static final int MIN_ALPHA = 4;
    /** The frame reaches this far around its content box, like brassTooltip. / 外框超出内容框的宽度，与 brassTooltip 相同。 */
    static final int FRAME = 4;

    private static final String[] WISP = {"..#..", ".##..", ".###.", "#####", "##.##", "##.##", ".###."};

    private MediumPaint() {
    }

    /** Footprint 5x7 at (x, y): the ghost-fire wisp. / 5x7 的鬼火图标。 */
    static void wisp(DrawContext c, int x, int y, int col) {
        InventoryCardPaint.bitmap(c, x, y, col, WISP);
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
        InventoryCardPaint.roundedFill(c, x, y, w, h, body);
        c.fill(x + 1, y, x + w - 1, y + 1, top);
        c.fill(x + 1, y + h - 1, x + w - 1, y + h, bottom);
        c.fillGradient(x, y + 1, x + 1, y + h - 1, top, bottom);
        c.fillGradient(x + w - 1, y + 1, x + w, y + h - 1, top, bottom);
    }

    static int voteLabelColor(boolean hovered, boolean chosen) {
        return chosen ? InventoryCardPaint.TEXT_HI : TarotLedgerPaint.tagButtonLabelColor(true, hovered);
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
}
