package dev.caecorthus.sparkwitch.client.witchmaiden.skin.screen;

import dev.caecorthus.sparkwitch.client.witchmaiden.skin.screen.MiniWitherSkinLayout.Rect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * A flat button in the SparkDLC settings style (no vanilla texture): a 1 px dark edge, a grey face that lightens on
 * hover or keyboard focus, white text. The accent variant (Done) is filled magenta.
 * SparkDLC 设置风格的扁平按钮（不用原版贴图）：1 像素深色边、悬停或键盘聚焦时变亮的灰色按钮面、白色文字。强调样式（完成）
 * 为洋红填充。
 */
final class MiniWitherSkinFlatButton extends ButtonWidget {
    private final boolean accent;

    MiniWitherSkinFlatButton(Rect at, Text message, boolean accent, PressAction onPress) {
        super(at.x(), at.y(), at.width(), at.height(), message, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.accent = accent;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        boolean keyboard = MiniWitherSkinDraw.keyboardFocus(isFocused());
        boolean hot = this.active && (isHovered() || keyboard);
        int x = getX();
        int y = getY();
        int face = accent ? (hot ? MiniWitherSkinPalette.ACCENT_HOVER : MiniWitherSkinPalette.ACCENT)
                : (hot ? MiniWitherSkinPalette.BUTTON_HOVER : MiniWitherSkinPalette.BUTTON);
        context.fill(x, y, x + width, y + height, MiniWitherSkinPalette.BUTTON_EDGE);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, face);
        if (keyboard) {
            MiniWitherSkinDraw.outline(context, x, y, width, height, MiniWitherSkinPalette.TEXT);
        }
        int color = this.active ? MiniWitherSkinPalette.TEXT : MiniWitherSkinPalette.TEXT_DISABLED;
        context.drawCenteredTextWithShadow(font, MiniWitherSkinDraw.fit(font, getMessage(), width - 8),
                x + width / 2, y + (height - font.fontHeight) / 2 + 1, color);
    }
}
