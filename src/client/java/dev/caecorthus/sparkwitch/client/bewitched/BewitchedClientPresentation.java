package dev.caecorthus.sparkwitch.client.bewitched;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.witch.bewitched.BewitchedPlayerComponent;
import dev.caecorthus.sparkwitch.roles.witch.bewitched.BewitchedRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * Role-owned HUD line of a living Bewitched (魔化使): its promotion progress {@code n/2}, bottom-right like the Grand
 * Witch's lines (a Bewitched owns no witch skill, so the shared skill line never draws there). It reads only the local
 * player's own synced role and owner-synced {@code sparkwitch:bewitched} count; the server alone counts and promotes.
 * Never part of the witch skill inventory panel.
 * 存活魔化使的职业自有 HUD 行：右下角显示晋升进度 {@code n/2}，位置与大魔女的提示行相同（魔化使没有魔女技能，共享技能行
 * 不会在此绘制）。只读取本地玩家自己同步的身份与仅同步给本人的 {@code sparkwitch:bewitched} 计数；计数与晋升只由服务端决定。
 * 从不属于魔女技能背包面板。
 */
public final class BewitchedClientPresentation {
    static final String PROGRESS_KEY = "hud.sparkwitch.bewitched.progress";
    private static final int RIGHT_PADDING = 5;
    private static final int BOTTOM_PADDING = 5;
    private static boolean registered;

    private BewitchedClientPresentation() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (SparkWitchServerConnection.isConfirmedServer()) {
                render(context);
            }
        });
    }

    /** Exactly a living Bewitched on this client (real synced role). / 本客户端上恰为存活的魔化使（真实同步身份）。 */
    public static boolean isLivingBewitched(ClientPlayerEntity player) {
        return GameFunctions.isPlayerPlayingAndAlive(player)
                && BewitchedRules.isBewitched(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    public static Text progressLine(int tasks) {
        return Text.translatable(PROGRESS_KEY, Math.clamp(tasks, 0, BewitchedRules.PROMOTION_TASKS),
                BewitchedRules.PROMOTION_TASKS);
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        // HudRenderCallback fires even with F1, unlike Wathe's own HUD layer; follow Wathe's train HUD.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同；跟随 Wathe 列车 HUD 的显示状态。
        if (player == null || client.options.hudHidden
                || WatheClient.trainComponent == null || !WatheClient.trainComponent.hasHud()
                || !isLivingBewitched(player)) {
            return;
        }
        TextRenderer renderer = client.textRenderer;
        Text line = progressLine(BewitchedPlayerComponent.KEY.get(player).getPromotionTasks());
        int x = context.getScaledWindowWidth() - RIGHT_PADDING - renderer.getWidth(line);
        int y = context.getScaledWindowHeight() - BOTTOM_PADDING - renderer.fontHeight;
        context.drawTextWithShadow(renderer, line, x, y, BewitchedRules.COLOR);
    }
}
