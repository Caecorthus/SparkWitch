package dev.caecorthus.sparkwitch.client.witchmaiden.skin.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Language;

/** Small flat drawing helpers shared by the screen's widgets. / 界面控件共用的扁平绘制小工具。 */
final class MiniWitherSkinDraw {
    static final Text ELLIPSIS = Text.literal("…");

    private MiniWitherSkinDraw() {
    }

    /** {@code text} cut to {@code maxWidth} with "…" when it does not fit. / 放不下时截断并加「…」。 */
    static OrderedText fit(TextRenderer font, StringVisitable text, int maxWidth) {
        if (font.getWidth(text) <= maxWidth) {
            return Language.getInstance().reorder(text);
        }
        StringVisitable head = font.trimToWidth(text, Math.max(0, maxWidth - font.getWidth(ELLIPSIS)));
        return Language.getInstance().reorder(StringVisitable.concat(head, ELLIPSIS));
    }

    static boolean fits(TextRenderer font, StringVisitable text, int maxWidth) {
        return font.getWidth(text) <= maxWidth;
    }

    /** A filled box with its four corner pixels cut, which reads as rounded. / 去掉四角像素的填充框，看起来是圆角。 */
    static void chip(DrawContext context, int x, int y, int width, int height, int color) {
        if (width < 3 || height < 3) {
            return;
        }
        context.fill(x + 1, y, x + width - 1, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    /** A 5×5 dot with its corners cut. / 去掉四角的 5×5 圆点。 */
    static void dot(DrawContext context, int x, int y, int color) {
        context.fill(x + 1, y, x + 4, y + 5, color);
        context.fill(x, y + 1, x + 5, y + 4, color);
    }

    static final int SPEAKER_WIDTH = 8;
    static final int SPEAKER_HEIGHT = 7;

    /** An 8×7 pixel speaker: box, cone and two sound waves. / 8×7 像素喇叭：箱体、喇叭口与两道声波。 */
    static void speaker(DrawContext context, int x, int y, int color) {
        context.fill(x, y + 2, x + 2, y + 5, color);
        context.fill(x + 2, y + 1, x + 3, y + 6, color);
        context.fill(x + 3, y, x + 4, y + 7, color);
        context.fill(x + 5, y + 2, x + 6, y + 5, color);
        context.fill(x + 6, y + 1, x + 7, y + 2, color);
        context.fill(x + 7, y + 2, x + 8, y + 5, color);
        context.fill(x + 6, y + 5, x + 7, y + 6, color);
    }

    /** A 1 px outline inside the box. / 框内 1 像素描边。 */
    static void outline(DrawContext context, int x, int y, int width, int height, int color) {
        context.drawBorder(x, y, width, height, color);
    }

    static boolean keyboardFocus(boolean focused) {
        return focused && MinecraftClient.getInstance().getNavigationType().isKeyboard();
    }
}
