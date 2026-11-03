package dev.caecorthus.sparkwitch.client.recruit;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.recruit.RecruitPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.recruit.RecruitRules;
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
 * Role-owned HUD line of a living Recruit: the bottom-right first-task progress {@code n/1} until Wathe's knife is
 * granted. It reads only the local player's own synced role and owner-synced {@code sparkwitch:recruit} state; the
 * server alone counts tasks and grants the knife.
 * 存活新兵的职业自有 HUD 行：发刀前在右下角显示首个任务进度 {@code n/1}。只读取本地玩家自己同步的身份与仅同步给本人的
 * {@code sparkwitch:recruit} 状态；任务计数与发刀只由服务端决定。
 */
public final class RecruitClientPresentation {
    static final String PROGRESS_KEY = "hud.sparkwitch.recruit.knife_progress";
    private static final int RIGHT_PADDING = 5;
    private static final int BOTTOM_PADDING = 5;
    private static boolean registered;

    private RecruitClientPresentation() {
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

    /** Exactly a living Recruit on this client (real synced role). / 本客户端上恰为存活的新兵（真实同步身份）。 */
    public static boolean isLivingRecruit(ClientPlayerEntity player) {
        return GameFunctions.isPlayerPlayingAndAlive(player)
                && RecruitRules.isRecruit(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    public static Text progressLine(int tasks) {
        return Text.translatable(PROGRESS_KEY, Math.clamp(tasks, 0, RecruitRules.REQUIRED_TASKS),
                RecruitRules.REQUIRED_TASKS);
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        // HudRenderCallback fires even with F1, unlike Wathe's own HUD layer; follow Wathe's train HUD.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同；跟随 Wathe 列车 HUD 的显示状态。
        if (player == null || client.options.hudHidden
                || WatheClient.trainComponent == null || !WatheClient.trainComponent.hasHud()
                || !isLivingRecruit(player)
                || RecruitPlayerComponent.KEY.get(player).isKnifeGranted()) {
            return;
        }
        TextRenderer renderer = client.textRenderer;
        Text line = progressLine(RecruitPlayerComponent.KEY.get(player).getTasksCompleted());
        int x = context.getScaledWindowWidth() - RIGHT_PADDING - renderer.getWidth(line);
        int y = context.getScaledWindowHeight() - BOTTOM_PADDING - renderer.fontHeight;
        context.drawTextWithShadow(renderer, line, x, y, RecruitRules.ROLE_COLOR);
    }
}
