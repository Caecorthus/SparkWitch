package dev.caecorthus.sparkwitch.client.vendetta;

import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaPresentationRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/** Renders Vendetta's private reveal cycle at the lower right. / 在右下角渲染仇杀客私有透视循环。 */
public final class VendettaHudRenderer {
    private static final int RIGHT_PADDING = 5;
    private static final int BOTTOM_PADDING = 5;

    private VendettaHudRenderer() {
    }

    public static void render(DrawContext context, ClientPlayerEntity player) {
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        Text line = stateText(player);
        int x = context.getScaledWindowWidth() - RIGHT_PADDING - renderer.getWidth(line);
        int y = context.getScaledWindowHeight() - BOTTOM_PADDING - renderer.fontHeight;
        context.drawTextWithShadow(renderer, line, x, y, VendettaPresentationRules.KILLER_HIGHLIGHT_COLOR);
    }

    static Text stateText(ClientPlayerEntity player) {
        VendettaPlayerComponent component = VendettaPlayerComponent.KEY.get(player);
        if (component.getRevealActiveTicks() > 0) {
            return Text.translatable(
                    "hud.sparkwitch.vendetta.reveal_remaining",
                    VendettaPresentationRules.secondsRemaining(component.getRevealActiveTicks())
            );
        }
        return Text.translatable(
                "hud.sparkwitch.vendetta.reveal_countdown",
                VendettaPresentationRules.secondsRemaining(component.getRevealCooldownTicks())
        );
    }
}
