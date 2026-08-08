package dev.caecorthus.sparkwitch.client.kidnapper;

import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperRules;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/** 绘制迷药控制期间的黑屏、警告和剩余时间。 */
public final class KidnapperControlledHudRenderer {
    private KidnapperControlledHudRenderer() {
    }

    public static void render(DrawContext context, ClientPlayerEntity player) {
        if (!GameFunctions.isPlayerAliveAndSurvival(player)) {
            return;
        }
        KidnapperControlComponent component = KidnapperControlComponent.KEY.get(player);
        if (!component.isControlled()) {
            return;
        }

        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        context.fill(0, 0, width, height, 0xFF000000);

        MinecraftClient client = MinecraftClient.getInstance();
        int remainingSeconds = Math.max(1, (component.getControlTicks() + 19) / 20);
        context.drawCenteredTextWithShadow(
                client.textRenderer,
                Text.translatable("message.sparkwitch.kidnapper.warning"),
                width / 2,
                height / 2 - 10,
                KidnapperRules.COLOR
        );
        context.drawCenteredTextWithShadow(
                client.textRenderer,
                Text.translatable("message.sparkwitch.kidnapper.timeleft", remainingSeconds),
                width / 2,
                height / 2 + 10,
                KidnapperRules.COLOR
        );
    }
}
