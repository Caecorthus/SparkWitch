package dev.caecorthus.sparkwitch.client.magician;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.client.SparkWitchClient;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlayerComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/** 魔术师右下角状态提示，保持自改版的四阶段文案。 */
public final class MagicianStatusHudRenderer {
    /** Half the vanilla hotbar width plus the attack indicator next to it. / 原版快捷栏半宽加上其旁边的攻击指示器。 */
    private static final int HOTBAR_HALF_WIDTH = 91 + 20;
    /** Vanilla hotbar height. / 原版快捷栏高度。 */
    private static final int HOTBAR_HEIGHT = 22;
    private MagicianStatusHudRenderer() {}
    public static void render(DrawContext context, ClientPlayerEntity player) {
        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!GameFunctions.isPlayerPlayingAndAlive(player)
                || role == null
                || !SparkWitchRoles.MAGICIAN_ID.equals(role.identifier())) return;
        MagicianPlayerComponent c=MagicianPlayerComponent.KEY.get(player);
        Text key = SparkWitchClient.abilityKeyText();
        Text line = switch(c.stage()) {
            case RECORDING -> Text.translatable("hud.sparkwitch.magician.recording", seconds(c.stageTicks()), key);
            case READY_PLAYBACK -> Text.translatable("hud.sparkwitch.magician.ready", key);
            case PLAYING -> Text.translatable("hud.sparkwitch.magician.playing", seconds(c.stageTicks()), key);
            default -> c.cooldownTicks() > 0
                    ? Text.translatable("hud.sparkwitch.magician.cooldown", seconds(c.cooldownTicks()))
                    : Text.translatable("hud.sparkwitch.magician.idle", c.selectedName(), key);
        };
        var renderer=MinecraftClient.getInstance().textRenderer;
        int width = context.getScaledWindowWidth();
        int x = width - 5 - renderer.getWidth(line);
        int y = context.getScaledWindowHeight() - 5 - renderer.fontHeight;
        // At a high GUI scale the right-aligned line reaches the hotbar; lift it above the hotbar row instead.
        // 高 GUI 缩放时右对齐的提示会压到快捷栏；此时改为绘制在快捷栏上方。
        if (x < width / 2 + HOTBAR_HALF_WIDTH) y -= HOTBAR_HEIGHT;
        context.drawTextWithShadow(renderer, line, x, y, 0x6B17B0);
    }
    private static int seconds(int ticks){ return Math.max(0,(ticks+19)/20); }
}
