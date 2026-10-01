package dev.caecorthus.sparkwitch.client.fisher;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Glimmerfish instinct presentation on Wathe's {@code GetInstinctHighlight} event (client only): the glimmering local
 * player sees every other living player through walls, keyless, in one colour; every other non-spectator viewer loses
 * the outline of a glimmering player. Wraith privacy (a HEAD cancel on Wathe's highlight method), SparkTraits' public
 * {@code isInstinctHidden} and the fear/obscure suppressors still win.
 * 灵光鱼的本能展示，挂在 Wathe 的 {@code GetInstinctHighlight} 事件上（仅客户端）：灵光中的本地玩家无需按键、
 * 以单一颜色隔墙看到其他所有存活玩家；其他非旁观者看不到灵光中玩家的描边。冤魂隐私（Wathe 高亮方法头部取消）、
 * SparkTraits 公开的 {@code isInstinctHidden} 以及恐惧 / 障眼压制仍然优先。
 */
public final class FisherGlimmerInstinctHooks {
    private static boolean registered;

    private FisherGlimmerInstinctHooks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GetInstinctHighlight.EVENT.register(FisherGlimmerInstinctHooks::highlight);
    }

    @Nullable
    private static GetInstinctHighlight.HighlightResult highlight(Entity target) {
        if (!(target instanceof PlayerEntity targetPlayer) || !SparkWitchServerConnection.isConfirmedServer()) {
            return null;
        }
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null) {
            return null;
        }
        boolean targetIsViewer = viewer == targetPlayer;
        boolean viewerSpectating = viewer.isSpectator();
        boolean targetGlimmering = FisherGlimmerClient.isGlimmering(targetPlayer);
        if (FisherGlimmerClientRules.hidesTarget(targetGlimmering, viewerSpectating, targetIsViewer)) {
            return new GetInstinctHighlight.HighlightResult(-1, false, FisherGlimmerClientRules.HIDE_PRIORITY);
        }
        boolean viewerGlimmering = FisherGlimmerClient.isGlimmering(viewer);
        if (!viewerGlimmering) {
            return null;
        }
        boolean outlines = FisherGlimmerClientRules.outlines(true, viewerSpectating, targetIsViewer,
                GameFunctions.isPlayerAliveAndSurvival(targetPlayer), targetGlimmering,
                FisherInstinctVisibilityBridge.isHidden(viewer, targetPlayer));
        return outlines
                ? GetInstinctHighlight.HighlightResult.always(FisherGlimmerClientRules.OUTLINE_COLOR,
                        FisherGlimmerClientRules.OUTLINE_PRIORITY)
                : null;
    }
}
