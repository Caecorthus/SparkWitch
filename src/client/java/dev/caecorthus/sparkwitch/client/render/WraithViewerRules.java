package dev.caecorthus.sparkwitch.client.render;

import dev.caecorthus.sparkwitch.client.vendetta.VendettaClientPresentation;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.Faction;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Shared owner, ordinary-viewer, and spectator rules for Wraith presentation.
 * 统一冤魂本人、普通观察者与旁观者的显示规则。
 */
public final class WraithViewerRules {
    private WraithViewerRules() {
    }

    public static boolean shouldRevealPromotedSaboteurToKiller(PlayerEntity viewer, PlayerEntity target) {
        if (viewer == null || target == null || viewer == target || viewer.isSpectator()
                || !SaboteurRules.isActivePromotedSaboteur(target)) {
            return false;
        }
        Role viewerRole = GameWorldComponent.KEY.get(viewer.getWorld()).getRole(viewer);
        return viewerRole != null && viewerRole.getFaction() == Faction.KILLER;
    }

    public static boolean shouldHideFromOrdinaryViewer(PlayerEntity viewer, PlayerEntity target) {
        return viewer != null
                && target != null
                && WraithClientState.isActive(target)
                && !viewer.getUuid().equals(target.getUuid())
                && !viewer.isSpectator()
                && !shouldRevealPromotedSaboteurToKiller(viewer, target)
                && !VendettaClientPresentation.isBoundKillerViewingVendetta(viewer, target);
    }

    public static boolean shouldRevealToSpectator(PlayerEntity viewer, PlayerEntity target) {
        return viewer != null
                && target != null
                && viewer.isSpectator()
                && WraithClientState.isActive(target);
    }
}
