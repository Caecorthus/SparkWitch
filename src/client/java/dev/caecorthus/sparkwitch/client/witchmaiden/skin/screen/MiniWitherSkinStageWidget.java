package dev.caecorthus.sparkwitch.client.witchmaiden.skin.screen;

import dev.caecorthus.sparkwitch.client.witchmaiden.skin.MiniWitherSkinPreview;
import dev.caecorthus.sparkwitch.client.witchmaiden.skin.screen.MiniWitherSkinLayout.Rect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * The preview stage: a 1 px frame with the current Mini Wither look inside, at its in-game size. It spins slowly;
 * dragging inside turns it, and with keyboard focus Left/Right turn it in 15° steps (see {@link MiniWitherPreviewSpin}).
 * 预览舞台：1 像素边框内按游戏内实际大小显示当前的小凋灵外观。它缓慢旋转；在框内拖动可转动它，键盘聚焦时左右键每次转 15°
 * （见 {@link MiniWitherPreviewSpin}）。
 */
final class MiniWitherSkinStageWidget extends ClickableWidget {
    private static final Logger LOGGER = LoggerFactory.getLogger("SparkWitch/MiniWitherSkin");

    private final MiniWitherPreviewSpin spin;
    private boolean failureLogged;

    MiniWitherSkinStageWidget(Rect at, Text caption, MiniWitherPreviewSpin spin) {
        super(at.x(), at.y(), at.width(), at.height(), caption);
        this.spin = spin;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        if (width <= 2 || height <= 2) {
            return;
        }
        context.fill(x + 1, y + 1, getRight() - 1, getBottom() - 1, MiniWitherSkinPalette.STAGE_FILL);
        boolean drawn;
        context.enableScissor(x + 1, y + 1, getRight() - 1, getBottom() - 1);
        try {
            MiniWitherSkinPreview.render(context, x + width / 2, y + height / 2, width - 2, delta,
                    spin.yaw(Util.getMeasuringTimeMs()));
            drawn = true;
        } catch (RuntimeException | StackOverflowError failure) {
            // A preview failure must never close the settings: log once and keep the empty frame.
            // 预览失败绝不能关闭设置界面：只记录一次日志，并保留空框。
            if (!failureLogged) {
                failureLogged = true;
                LOGGER.warn("Mini Wither preview failed; showing a placeholder", failure);
            }
            drawn = false;
        } finally {
            context.disableScissor();
        }
        if (!drawn) {
            drawPlaceholder(context);
        }
        boolean keyboard = MiniWitherSkinDraw.keyboardFocus(isFocused());
        MiniWitherSkinDraw.outline(context, x, y, width, height,
                keyboard ? MiniWitherSkinPalette.TEXT : MiniWitherSkinPalette.STAGE_FRAME);
    }

    private void drawPlaceholder(DrawContext context) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        List<OrderedText> lines = font.wrapLines(Text.translatable(MiniWitherSkinLangKeys.PREVIEW_UNAVAILABLE),
                Math.max(1, width - 8));
        int lineY = getY() + height / 2 - lines.size() * font.fontHeight / 2;
        for (OrderedText line : lines) {
            context.drawCenteredTextWithShadow(font, line, getX() + width / 2, lineY, MiniWitherSkinPalette.TEXT_MUTED);
            lineY += font.fontHeight;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active || !this.visible || button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        spin.press(Util.getMeasuringTimeMs(), mouseX);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!spin.dragging()) {
            return false;
        }
        spin.drag(mouseX);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!spin.dragging()) {
            return false;
        }
        spin.release(Util.getMeasuringTimeMs());
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode != GLFW.GLFW_KEY_LEFT && keyCode != GLFW.GLFW_KEY_RIGHT) {
            return false;
        }
        spin.nudge(Util.getMeasuringTimeMs(), keyCode == GLFW.GLFW_KEY_LEFT
                ? -MiniWitherPreviewSpin.KEY_STEP_DEGREES : MiniWitherPreviewSpin.KEY_STEP_DEGREES);
        return true;
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(NarrationPart.TITLE, getMessage());
    }
}
