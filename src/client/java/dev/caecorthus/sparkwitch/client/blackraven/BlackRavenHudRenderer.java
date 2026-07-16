package dev.caecorthus.sparkwitch.client.blackraven;

import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityController;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/** Black-Raven-owned row immediately above the shared primary-skill line. / 黑羽鸦自有的第二行提示，位于共享主技能行正上方。 */
public final class BlackRavenHudRenderer {
    private static final int RIGHT_PADDING = 5;
    private static final int BOTTOM_PADDING = 5;
    private static final int ROW_GAP = 2;
    private static final int DISABLED_COLOR = 0x777777;

    private BlackRavenHudRenderer() {
    }

    public static void render(DrawContext context, ClientPlayerEntity player) {
        if (!GameFunctions.isPlayerPlayingAndAlive(player)
                || !GameWorldComponent.KEY.get(player.getWorld()).isRunning()
                || !BlackRavenRules.isBlackRaven(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return;
        }

        Text line = Text.translatable(
                BlackRavenClientState.isSensedMode()
                        ? "hud.sparkwitch.black_raven.instinct.sensed"
                        : "hud.sparkwitch.black_raven.instinct.normal",
                SecondaryAbilityController.secondaryKeyText()
        );
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        int x = context.getScaledWindowWidth() - RIGHT_PADDING - renderer.getWidth(line);
        int y = context.getScaledWindowHeight()
                - BOTTOM_PADDING
                - renderer.fontHeight * 2
                - ROW_GAP;
        int color = BlackRavenClientState.isPerceptionActive(player) ? DISABLED_COLOR : BlackRavenRules.COLOR;
        context.drawTextWithShadow(renderer, line, x, y, color);
    }
}
