package dev.caecorthus.sparkwitch.client.witchmaiden.skin.screen;

import dev.caecorthus.sparkwitch.client.witchmaiden.skin.screen.MiniWitherSkinLayout.Rect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;

/**
 * One sound preview button: a pixel speaker and the slot name on a flat face, outlined in magenta while its preview is
 * playing. Silent itself, because the press plays the slot's sound.
 * 一个试听按钮：扁平按钮面上的像素喇叭与槽位名称，试听播放期间描洋红边。按钮自身不发声，因为按下即播放该槽位的声音。
 */
final class MiniWitherSkinSoundButton extends ButtonWidget {
    private static final int PAD = 4;
    private static final int ICON_GAP = 3;
    private final BooleanSupplier playing;

    MiniWitherSkinSoundButton(Rect at, Text message, BooleanSupplier playing, PressAction onPress) {
        super(at.x(), at.y(), at.width(), at.height(), message, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.playing = playing;
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        boolean keyboard = MiniWitherSkinDraw.keyboardFocus(isFocused());
        boolean hot = this.active && (isHovered() || keyboard);
        boolean sounding = playing.getAsBoolean();
        int x = getX();
        int y = getY();
        context.fill(x, y, x + width, y + height, MiniWitherSkinPalette.BUTTON_EDGE);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1,
                hot ? MiniWitherSkinPalette.BUTTON_HOVER : MiniWitherSkinPalette.BUTTON);
        if (sounding) {
            MiniWitherSkinDraw.outline(context, x, y, width, height, MiniWitherSkinPalette.ACCENT);
        } else if (keyboard) {
            MiniWitherSkinDraw.outline(context, x, y, width, height, MiniWitherSkinPalette.TEXT);
        }
        int icon = MiniWitherSkinDraw.SPEAKER_WIDTH + ICON_GAP;
        OrderedText label = MiniWitherSkinDraw.fit(font, getMessage(), Math.max(0, width - 2 * PAD - icon));
        int left = x + Math.max(PAD, (width - icon - font.getWidth(label)) / 2);
        int iconColor = sounding ? MiniWitherSkinPalette.ACCENT_HOVER
                : hot ? MiniWitherSkinPalette.TEXT : MiniWitherSkinPalette.TEXT_MUTED;
        MiniWitherSkinDraw.speaker(context, left, y + (height - MiniWitherSkinDraw.SPEAKER_HEIGHT) / 2, iconColor);
        context.drawTextWithShadow(font, label, left + icon, y + (height - font.fontHeight) / 2 + 1,
                hot || sounding ? MiniWitherSkinPalette.TEXT : MiniWitherSkinPalette.TEXT_BODY);
    }
}
