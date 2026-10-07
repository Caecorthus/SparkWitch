package dev.caecorthus.sparkwitch.client.judge;

import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenClientState;
import dev.caecorthus.sparkwitch.client.blackraven.SparkTraitsInstinctVisibilityBridge;
import dev.caecorthus.sparkwitch.client.hooks.WitchInstinctSuppressionClientHooks;
import dev.caecorthus.sparkwitch.client.render.WraithClientState;
import dev.caecorthus.sparkwitch.client.render.WraithViewerRules;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeRules;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeWorldComponent;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.morphling.MorphlingPlayerComponent;

/** Sentence-only fallback, never a global hide or private role reveal. / 仅判刑后备色，不全局隐藏或暴露私有身份。 */
public final class JudgeClientHooks {
    private JudgeClientHooks() {
    }

    public static int resolveHighlight(int originalColor, Entity entity) {
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        if (originalColor != -1 || !SparkWitchServerConnection.isConfirmedServer()
                || viewer == null || !(entity instanceof PlayerEntity target) || viewer == target
                || !GameFunctions.isPlayerPlayingAndAlive(viewer)
                || !GameFunctions.isPlayerPlayingAndAlive(target)
                || GameFunctions.isPlayerSpectatingOrCreative(viewer)
                || GameFunctions.isPlayerSpectatingOrCreative(target)) {
            return originalColor;
        }
        if (GameWorldComponent.KEY.get(viewer.getWorld()).getGameStatus() != GameWorldComponent.GameStatus.ACTIVE
                || !JudgeWorldComponent.KEY.get(viewer.getWorld()).isSentenced(target.getUuid())
                || isHardHidden(viewer, target)) {
            return originalColor;
        }
        // As with factor fallback, Traits HEAD may skip event dispatch; honor explicit skip results.
        // 与因子后备逻辑一致，Traits HEAD 可能跳过事件派发，补色前尊重显式跳过结果。
        GetInstinctHighlight.HighlightResult event = GetInstinctHighlight.EVENT.invoker().getHighlight(target);
        return JudgeClientRules.resolveOutline(originalColor, true, false,
                event != null && event.isSkip(), true, JudgeRules.OUTLINE_COLOR);
    }

    private static boolean isHardHidden(PlayerEntity viewer, PlayerEntity target) {
        return target.isInvisible() || target.isInvisibleTo(viewer)
                || SparkTraitsInstinctVisibilityBridge.isHidden(viewer, target)
                || MorphlingPlayerComponent.KEY.get(target).corpseMode
                || WraithClientState.isRestricted(viewer)
                || WraithViewerRules.shouldHideFromOrdinaryViewer(viewer, target)
                || WitchInstinctSuppressionClientHooks.shouldSuppressInstinctHighlight()
                || WitchInstinctSuppressionClientHooks.shouldSuppressSwallowedInstinctHighlight(target)
                || (BlackRavenClientState.isEligible(viewer)
                    && (BlackRavenClientState.isPerceptionActive(viewer)
                        || BlackRavenClientState.mode() == BlackRavenClientState.InstinctMode.SENSED_ONLY));
    }
}
