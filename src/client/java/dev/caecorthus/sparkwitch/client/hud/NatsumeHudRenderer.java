package dev.caecorthus.sparkwitch.client.hud;

import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.client.natsume.NatsumeClientState;
import dev.caecorthus.sparkwitch.roles.killer.natsume.NatsumeComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 夏目安安's single readout: hypnosis ready, or seconds until it is, from her own synced cooldown seconds. Drawn by the
 * {@code RoleHudDispatcher.renderNatsume} layer, which owns the confirmed-server and liveness gates; both keys are
 * exempt from the hypnosis HUD garble, so it stays readable if she hypnotised herself.
 * 夏目安安的一行信息：催眠就绪，或还要等几秒，读取她自身同步的冷却秒数。由 {@code RoleHudDispatcher.renderNatsume} 层绘制，
 * 已确认服务器与存活门槛由该层负责；两个翻译键都豁免催眠 HUD 乱码，因此她催眠自己时也保持可读。
 */
public final class NatsumeHudRenderer {

    private NatsumeHudRenderer() {
    }

    public static void render(DrawContext context, ClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || client.currentScreen != null) {
            return;
        }
        if (!NatsumeClientState.isNatsume(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return;
        }
        NatsumeComponent component = NatsumeComponent.KEY.getNullable(player);
        if (component == null) {
            return;
        }
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
