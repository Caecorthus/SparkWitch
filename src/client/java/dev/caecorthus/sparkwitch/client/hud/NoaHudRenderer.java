package dev.caecorthus.sparkwitch.client.hud;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.roles.civilian.noa.NoaComponent;
import dev.caecorthus.sparkwitch.roles.civilian.noa.NoaRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 诺亚's single readout line: painting ready, or how long until it is.
 * 诺亚的一行信息：作画就绪，或还要等多久。
 */
public final class NoaHudRenderer {

    private static final int LINE_HEIGHT = 10;
    private static final int MARGIN = 6;

    private NoaHudRenderer() {
    }

    /**
     * Sanity percentage, centred just above the action bar — not an action-bar message.
     * 理智百分比，居中显示在动作栏上方——不是动作栏消息。
     */
    private static void renderSanityPercent(DrawContext context, MinecraftClient client, ClientPlayerEntity player) {
        float mood = PlayerMoodComponent.KEY.get(player).getMood();
        // Always shown, including negative values (owner 2026-11-03). 常显，负值也显示。
        int percent = Math.max(-100, Math.min(100, Math.round(mood * 100.0F)));
        Text line = Text.translatable("hud.sparkwitch.noa.sanity", percent);
        context.drawCenteredTextWithShadow(client.textRenderer, line,
                context.getScaledWindowWidth() / 2,
                context.getScaledWindowHeight() - 96, 0xFF9CC8FC);
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.options.hudHidden || client.currentScreen != null) {
            return;
        }
        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (role == null || !NoaRules.ROLE_ID.equals(role.identifier())) {
            return;
        }
        NoaComponent component = NoaComponent.KEY.get(player);
        renderSanityPercent(context, client, player);
        Text line = component.getPaintCooldownSeconds() > 0
                ? Text.translatable("hud.sparkwitch.noa.paint_cooldown",
                        component.getPaintCooldownSeconds()).formatted(Formatting.GRAY)
                : Text.translatable("hud.sparkwitch.noa.paint",
                        SparkWitchClient.abilityKeyText()).formatted(Formatting.AQUA);
        // Skill readout sits just above the action bar, not in a corner (owner 2026-11-03).
        // 技能提示放在动作栏正上方，不再画在角落里（所有者 2026-11-03）。
        context.drawCenteredTextWithShadow(client.textRenderer, line,
                context.getScaledWindowWidth() / 2,
                context.getScaledWindowHeight() - 86, 0xFFFFFF);
    }
}
