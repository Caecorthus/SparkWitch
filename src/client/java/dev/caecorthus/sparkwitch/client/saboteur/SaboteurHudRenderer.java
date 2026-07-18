package dev.caecorthus.sparkwitch.client.saboteur;

import dev.caecorthus.sparkwitch.client.hooks.WitchAbilityKeyBridge;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurRole;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/** Renders only Saboteur's two specified ability states. / 仅渲染破坏者规定的两种技能状态。 */
public final class SaboteurHudRenderer {
    private static final int RIGHT_PADDING = 5;
    private static final int BOTTOM_PADDING = 5;

    private SaboteurHudRenderer() {
    }

    public static void render(DrawContext context, ClientPlayerEntity player) {
        SaboteurPlayerComponent component = SaboteurPlayerComponent.KEY.maybeGet(player).orElse(null);
        if (component == null) {
            return;
        }

        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        Text line = stateText(component.getCooldownTicks());
        int x = context.getScaledWindowWidth() - RIGHT_PADDING - renderer.getWidth(line);
        int y = context.getScaledWindowHeight() - BOTTOM_PADDING - renderer.fontHeight;
        context.drawTextWithShadow(renderer, line, x, y, SaboteurRole.COLOR);
    }

    private static Text stateText(int cooldownTicks) {
        if (cooldownTicks > 0) {
            return Text.translatable(
                    "hud.sparkwitch.skill.sabotage.cooldown",
                    SaboteurHudRules.cooldownSeconds(cooldownTicks)
            );
        }
        return Text.translatable(
                "hud.sparkwitch.skill.sabotage.ready",
                WitchAbilityKeyBridge.keyText()
        );
    }
}
