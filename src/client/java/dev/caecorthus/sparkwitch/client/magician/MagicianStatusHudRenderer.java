package dev.caecorthus.sparkwitch.client.magician;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianStage;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/** 魔术师右下角状态提示，保持自改版的四阶段文案。 */
public final class MagicianStatusHudRenderer {
    private MagicianStatusHudRenderer() {}
    public static void render(DrawContext context, ClientPlayerEntity player) {
        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!GameFunctions.isPlayerPlayingAndAlive(player)
                || role == null
                || !SparkWitchRoles.MAGICIAN_ID.equals(role.identifier())) return;
        MagicianPlayerComponent c=MagicianPlayerComponent.KEY.get(player); String line;
        switch(c.stage()) {
            case RECORDING -> line="录制中，剩余 " + seconds(c.stageTicks()) + " 秒（按 " + SparkWitchClient.abilityKeyText().getString() + " 提前结束）";
            case READY_PLAYBACK -> line="按 " + SparkWitchClient.abilityKeyText().getString() + " 键以进行播放";
            case PLAYING -> line="播放中，剩余 " + seconds(c.stageTicks()) + " 秒（按 " + SparkWitchClient.abilityKeyText().getString() + " 提前结束）";
            default -> line=c.cooldownTicks() > 0
                    ? "魔术师技能冷却中：" + seconds(c.cooldownTicks()) + " 秒"
                    : "当前选择玩家：" + c.selectedName() + "；按 " + SparkWitchClient.abilityKeyText().getString() + " 键以进行录制";
        }
        var renderer=MinecraftClient.getInstance().textRenderer; int x=context.getScaledWindowWidth()-5-renderer.getWidth(line); int y=context.getScaledWindowHeight()-5-renderer.fontHeight; context.drawTextWithShadow(renderer, Text.literal(line),x,y,0x6B17B0);
    }
    private static int seconds(int ticks){ return Math.max(0,(ticks+19)/20); }
}
