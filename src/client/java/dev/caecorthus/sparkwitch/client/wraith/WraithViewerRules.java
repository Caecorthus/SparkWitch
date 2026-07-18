package dev.caecorthus.sparkwitch.client.wraith;

import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;

/** Resolves Wraith presentation without using Spectator or Creative mode as an authority signal. / 不以旁观或创造模式作为权限信号来裁决冤魂显示。 */
public final class WraithViewerRules {
    private WraithViewerRules() {
    }

    public static WraithViewerPolicy.Presentation presentation(PlayerEntity viewer, PlayerEntity target) {
        if (viewer == null || target == null) {
            return WraithViewerPolicy.Presentation.REAL;
        }
        return WraithViewerPolicy.presentation(
                WraithClientState.isActive(target),
                viewer.getUuid().equals(target.getUuid()),
                WraithClientState.isActive(viewer),
                GameFunctions.isPlayerPlayingAndAlive(viewer)
        );
    }

    public static boolean shouldRevealToWraithPeer(PlayerEntity viewer, PlayerEntity target) {
        return presentation(viewer, target) == WraithViewerPolicy.Presentation.ANONYMOUS_PEER;
    }

    public static boolean shouldHideFromViewer(PlayerEntity viewer, PlayerEntity target) {
        return presentation(viewer, target) == WraithViewerPolicy.Presentation.HIDDEN;
    }

    public static boolean shouldRevealWraithTarget(PlayerEntity viewer, PlayerEntity target) {
        WraithViewerPolicy.Presentation presentation = presentation(viewer, target);
        return presentation == WraithViewerPolicy.Presentation.ANONYMOUS_PEER
                || presentation == WraithViewerPolicy.Presentation.REAL && WraithClientState.isActive(target);
    }
}
