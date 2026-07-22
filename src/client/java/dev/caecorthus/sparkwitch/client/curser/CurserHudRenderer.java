package dev.caecorthus.sparkwitch.client.curser;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.roles.witch.curser.CurserPlayerComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/** Curser-owned bottom-right cooldown HUD. / 诅咒师自有的右下角冷却 HUD。 */
public final class CurserHudRenderer {
    private CurserHudRenderer() {
    }

    public static void render(DrawContext context, ClientPlayerEntity player) {
        if (!GameFunctions.isPlayerPlayingAndAlive(player)) {
            return;
        }
        int cooldown = CurserPlayerComponent.KEY.get(player).getCooldownTicks();
        Text line = cooldown > 0
                ? Text.translatable("hud.sparkwitch.curser.cooldown", seconds(cooldown))
                : Text.translatable("hud.sparkwitch.curser.ready", SparkWitchClient.abilityKeyText());
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        context.drawTextWithShadow(
                renderer,
                line,
                context.getScaledWindowWidth() - 5 - renderer.getWidth(line),
                context.getScaledWindowHeight() - 5 - renderer.fontHeight,
                0xA968D5
        );
    }

    private static int seconds(int ticks) {
        return (int) Math.ceil(ticks / 20.0);
    }
}
