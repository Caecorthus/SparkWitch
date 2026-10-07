package dev.caecorthus.sparkwitch.client.render;

import dev.caecorthus.sparkwitch.client.vendetta.VendettaClientPresentation;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.game.GameFunctions;
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
        return GameFunctions.isPlayerPlayingAndAlive(viewer)
                && viewerRole != null
                && viewerRole.getFaction() == Faction.KILLER;
    }

    public static boolean shouldHideFromOrdinaryViewer(PlayerEntity viewer, PlayerEntity target) {
        return viewer != null
                && target != null
                && WraithClientState.isActive(target)
                && !viewer.getUuid().equals(target.getUuid())
                && !isDeadSpectator(viewer)
                && !shouldRevealPromotedSaboteurToKiller(viewer, target)
                && !VendettaClientPresentation.isBoundKillerViewingVendetta(viewer, target);
    }

    public static boolean shouldRevealToSpectator(PlayerEntity viewer, PlayerEntity target) {
        return viewer != null
                && target != null
                && isDeadSpectator(viewer)
                && WraithClientState.isActive(target);
    }

    /**
     * Only a Wathe-dead spectator bypasses Wraith privacy. A living spectator (a Rift Gate occupant, a SparkTraits
     * Depression fake death) is an ordinary viewer, so holding instinct never shows it a Wraith's real role colour.
     * 只有已死亡（wathe 判定）的旁观者可越过冤魂隐私。存活的旁观者（裂隙门内玩家、SparkTraits 抑郁假死）属于普通
     * 观察者，按住本能键也看不到冤魂的真实职业颜色。
     */
    private static boolean isDeadSpectator(PlayerEntity viewer) {
        return viewer.isSpectator() && !GameFunctions.isPlayerPlayingAndAlive(viewer);
    }
}
