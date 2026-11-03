package dev.caecorthus.sparkwitch.client.hud;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.roles.killer.natsume.NatsumeComponent;
import dev.caecorthus.sparkwitch.roles.killer.natsume.NatsumeRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** 夏目安安's single readout: hypnosis ready, or seconds until it is. 夏目安安的一行信息：催眠就绪，或还要等几秒。 */
public final class NatsumeHudRenderer {

    private static final int LINE_HEIGHT = 10;
    private static final int MARGIN = 6;

    private NatsumeHudRenderer() {
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.options.hudHidden || client.currentScreen != null) {
            return;
        }
        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (role == null || !NatsumeRules.ROLE_ID.equals(role.identifier())) {
            return;
        }
        NatsumeComponent component = NatsumeComponent.KEY.get(player);
        Text line = component.getCooldownSeconds() > 0
                ? Text.translatable("message.sparkwitch.natsume.cooldown",
                        component.getCooldownSeconds()).formatted(Formatting.GRAY)
                : Text.translatable("hud.sparkwitch.natsume.ready",
                        SparkWitchClient.abilityKeyText()).formatted(Formatting.LIGHT_PURPLE);
        // Same slot as the other skill readouts: centred, just above the action bar.
        // 与其它技能提示同位置：居中，动作栏正上方。
        context.drawCenteredTextWithShadow(client.textRenderer, line,
                context.getScaledWindowWidth() / 2,
                context.getScaledWindowHeight() - 86, 0xFFFFFF);
    }
}
