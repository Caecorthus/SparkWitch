package dev.caecorthus.sparkwitch.client.hud;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorRules;
import dev.caecorthus.sparkwitch.util.TickTime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/** Bottom-right Savior G-skill line in the role color. / 右下角职业色的救世主 G 技能提示行。 */
public final class SaviorHudRenderer {
    private static final int RIGHT_PADDING = 5;
    private static final int BOTTOM_PADDING = 5;

    private SaviorHudRenderer() {
    }

    public static void render(DrawContext context, ClientPlayerEntity player) {
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        Text line = stateText(player);
        int x = context.getScaledWindowWidth() - RIGHT_PADDING - renderer.getWidth(line);
        int y = context.getScaledWindowHeight() - BOTTOM_PADDING - renderer.fontHeight;
        context.drawTextWithShadow(renderer, line, x, y, SaviorRules.ROLE_COLOR);
    }

    static Text stateText(ClientPlayerEntity player) {
        SaviorPlayerComponent component = SaviorPlayerComponent.KEY.get(player);
        if (component.getDomainTicks() > 0) {
            return Text.translatable(
                    "hud.sparkwitch.savior.domain_active",
                    TickTime.ceilSecondsAtLeastOne(component.getDomainTicks())
            );
        }
        if (component.getCooldownTicks() > 0) {
            return Text.translatable(
                    "hud.sparkwitch.savior.cooldown",
                    TickTime.ceilSecondsAtLeastOne(component.getCooldownTicks())
            );
        }
        return Text.translatable("hud.sparkwitch.savior.ready", SparkWitchClient.abilityKeyText());
    }
}
