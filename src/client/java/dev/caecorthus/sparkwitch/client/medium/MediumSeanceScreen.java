package dev.caecorthus.sparkwitch.client.medium;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumRules;
import dev.caecorthus.sparkwitch.roles.civilian.medium.SubmitMediumSeanceC2SPacket;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The 通灵 question box: up to {@link MediumRules#QUESTION_MAX_CHARS} characters, Enter sends the one paid submit and
 * Esc closes for free. The box auto-closes when the player is no longer a live Medium (death, role change, leaving the
 * server), so a paid window can never start from a stale screen. Everything is re-checked server-side.
 * 通灵提问框：最多 {@link MediumRules#QUESTION_MAX_CHARS} 字，回车发送唯一的付费提交，Esc 免费关闭。玩家不再是存活的
 * 通灵师（死亡、转职、离开服务器）时界面自动关闭，因此绝不会从一个过期界面开启付费窗口。一切由服务端重新校验。
 */
public final class MediumSeanceScreen extends Screen {
    private final int price;
    private String query = "";
    private boolean submitted;
    @Nullable
    private TextFieldWidget field;

    public MediumSeanceScreen(int price) {
        super(Text.translatable("screen.sparkwitch.medium.seance.title"));
        this.price = price;
    }

    @Override
    protected void init() {
        if (field != null) {
            query = field.getText();
        }
        int fieldWidth = Math.min(300, this.width - 40);
        field = new TextFieldWidget(
                this.textRenderer,
                (this.width - fieldWidth) / 2,
                this.height / 2 - 22,
                fieldWidth,
                20,
                Text.translatable("screen.sparkwitch.medium.seance.hint")
        );
        field.setMaxLength(MediumRules.QUESTION_MAX_CHARS);
        field.setPlaceholder(Text.translatable("screen.sparkwitch.medium.seance.hint"));
        field.setText(query);
        field.setChangedListener(text -> query = text);
        addDrawableChild(field);
        setInitialFocus(field);
        int buttonWidth = (fieldWidth - 8) / 2;
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.sparkwitch.medium.seance.cancel"), button -> close())
                .dimensions((this.width - fieldWidth) / 2, this.height / 2 + 6, buttonWidth, 20)
                .build());
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.sparkwitch.medium.seance.submit"), button -> submit())
                .dimensions((this.width - fieldWidth) / 2 + buttonWidth + 8, this.height / 2 + 6, buttonWidth, 20)
                .build());
    }

    @Override
    public void tick() {
        if (this.client == null || this.client.player == null
                || !SparkWitchServerConnection.isConfirmedServer()
                || !MediumRules.isMedium(this.client.player)
                || !GameFunctions.isPlayerPlayingAndAlive(this.client.player)) {
            close();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (submitted) {
            return true;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                close();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                submit();
                return true;
            }
            default -> {
            }
        }
        if (field != null) {
            field.keyPressed(keyCode, scanCode, modifiers);
        }
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (submitted) {
            return true;
        }
        return field != null && field.charTyped(chr, modifiers);
    }

    private void submit() {
        if (submitted || field == null) {
            return;
        }
        String question = field.getText().strip();
        if (question.isEmpty()) {
            return;
        }
        submitted = true;
        ClientPlayNetworking.send(new SubmitMediumSeanceC2SPacket(question));
        close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 62,
                0xFF000000 | MediumRules.COLOR);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("screen.sparkwitch.medium.seance.hint").formatted(Formatting.GRAY),
                this.width / 2, this.height / 2 - 48, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("screen.sparkwitch.medium.seance.price", price),
                this.width / 2, this.height / 2 - 36, 0xFFFFFF);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(null);
        }
    }
}
