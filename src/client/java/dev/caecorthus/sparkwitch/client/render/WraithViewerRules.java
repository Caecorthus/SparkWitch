package dev.caecorthus.sparkwitch.client.render;

import net.minecraft.entity.player.PlayerEntity;

/**
 * Shared owner, ordinary-viewer, and spectator rules for Wraith presentation.
 * 统一冤魂本人、普通观察者与旁观者的显示规则。
 */
public final class WraithViewerRules {
    private WraithViewerRules() {
    }

    public static boolean shouldHideFromOrdinaryViewer(PlayerEntity viewer, PlayerEntity target) {
        return viewer != null
                && target != null
                && WraithClientState.isActive(target)
                && !viewer.getUuid().equals(target.getUuid())
                && !viewer.isSpectator();
    }

    public static boolean shouldRevealToSpectator(PlayerEntity viewer, PlayerEntity target) {
        return viewer != null
                && target != null
                && viewer.isSpectator()
                && WraithClientState.isActive(target);
    }
}
