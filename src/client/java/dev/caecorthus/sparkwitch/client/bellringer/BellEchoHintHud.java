package dev.caecorthus.sparkwitch.client.bellringer;

import dev.caecorthus.sparkwitch.roles.killer.bellringer.BellEchoPlayerComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.client.gui.MoodRenderer;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.game.gamemode.MurderGameMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/**
 * Red top-left "heard the Bell Ringer's bell" hint, placed below Wathe's mood/task block. Called by
 * {@link BellRingerClient} only on a confirmed SparkWitch server; still checks the local player state.
 * The window is the owner-only synced countdown, so it shows only for players the server made hearers.
 * 红色左上角“听到敲钟人的钟声”提示，位于 Wathe 情绪/任务区块下方。仅在已确认的 SparkWitch 服务器上由
 * {@link BellRingerClient} 调用；仍需检查本地玩家状态。显示窗口来自仅拥有者同步的倒计时，
 * 因此只会出现在服务端判定为“听者”的玩家端。
 */
public final class BellEchoHintHud {
    private static final int HINT_COLOR = 0xFFFF5555;

    private BellEchoHintHud() {
    }

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        // HudRenderCallback fires even with F1, unlike Wathe's main HUD layer. / HudRenderCallback 在 F1 下仍会触发，与 Wathe 主 HUD 层不同。
        if (player == null || client.world == null || client.options.hudHidden) {
            return;
        }
        if (!GameFunctions.isPlayerPlayingAndAlive(player)
                || BellEchoPlayerComponent.KEY.get(player).heardRemainingTicks() <= 0) {
            return;
        }
        // Follow Wathe's own HUD visibility so the hint never floats alone. / 跟随 Wathe 自身 HUD 的显示状态，避免提示单独悬浮。
        if (WatheClient.trainComponent == null || !WatheClient.trainComponent.hasHud()) {
            return;
        }

        boolean moodBlockVisible = GameWorldComponent.KEY.get(client.world).getGameMode() instanceof MurderGameMode;
        boolean psychoActive = PlayerPsychoComponent.KEY.get(player).getPsychoTicks() > 0;
        TextRenderer renderer = client.textRenderer;
        // Read after Wathe's renderMainHud updated this frame's statics. / 在 Wathe renderMainHud 更新本帧静态值之后读取。
        int y = BellEchoHudRules.hintY(
                moodBlockVisible,
                psychoActive,
                MoodRenderer.moodOffset,
                MoodRenderer.moodRender,
                renderer.fontHeight
        );
        context.drawTextWithShadow(
                renderer,
                Text.translatable("hud.sparkwitch.bell_echo.heard"),
                BellEchoHudRules.TEXT_X,
                y,
                HINT_COLOR
        );
    }
}
