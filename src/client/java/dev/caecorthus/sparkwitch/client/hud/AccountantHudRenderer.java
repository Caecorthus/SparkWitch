package dev.caecorthus.sparkwitch.client.hud;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.roles.civilian.accountant.AccountantComponent;
import dev.caecorthus.sparkwitch.roles.civilian.accountant.AccountantRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** 会计's single readout: audit ready, or seconds until it is. 会计的一行信息：查账就绪，或还要等几秒。 */
public final class AccountantHudRenderer {

    private AccountantHudRenderer() {
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.options.hudHidden || client.currentScreen != null) {
            return;
        }
        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (role == null || !AccountantRules.ROLE_ID.equals(role.identifier())) {
            return;
        }
        AccountantComponent component = AccountantComponent.KEY.get(player);
        Text line = component.getCooldownSeconds() > 0
                ? Text.translatable("hud.sparkwitch.accountant.cooldown",
                        component.getCooldownSeconds()).formatted(Formatting.GRAY)
                : Text.translatable("hud.sparkwitch.accountant.ready",
                        SparkWitchClient.abilityKeyText()).formatted(Formatting.YELLOW);
        context.drawCenteredTextWithShadow(client.textRenderer, line,
                context.getScaledWindowWidth() / 2,
                context.getScaledWindowHeight() - 86, 0xFFFFFF);
    }
}
