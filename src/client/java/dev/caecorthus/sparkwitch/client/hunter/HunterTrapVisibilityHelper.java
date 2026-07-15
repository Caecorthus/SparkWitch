package dev.caecorthus.sparkwitch.client.hunter;

import dev.caecorthus.sparkwitch.client.hooks.WitchInstinctSuppressionClientHooks;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterRules;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterTrapEntity;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Resolves the client-side visibility decision shared by trap rendering and outlines.
 * 统一决定捕兽夹普通渲染与描边共用的客户端可见性。
 */
public final class HunterTrapVisibilityHelper {
    private HunterTrapVisibilityHelper() {
    }

    public static HunterRules.TrapVisibility visibilityFor(HunterTrapEntity trap, PlayerEntity viewer) {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return HunterRules.TrapVisibility.HIDDEN;
        }

        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(viewer.getWorld());
        if (!gameComponent.isRunning()) {
            return HunterRules.TrapVisibility.HIDDEN;
        }

        Role role = gameComponent.getRole(viewer);
        boolean nativeKiller = role != null && role.getFaction() == Faction.KILLER;
        boolean deadSpectator = isDeadMatchSpectator(viewer, gameComponent);
        boolean instinctActive = WatheClient.isInstinctEnabled()
                && !WitchInstinctSuppressionClientHooks.shouldSuppressInstinctHighlight();
        return HunterRules.trapVisibility(
                role == null ? null : role.identifier(),
                nativeKiller,
                deadSpectator,
                viewer.canSee(trap),
                instinctActive
        );
    }

    public static boolean usesAlwaysOnOutline(PlayerEntity viewer) {
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(viewer.getWorld());
        if (!gameComponent.isRunning()) {
            return false;
        }
        Role role = gameComponent.getRole(viewer);
        return (role != null && role.getFaction() == Faction.KILLER)
                || isDeadMatchSpectator(viewer, gameComponent);
    }

    private static boolean isDeadMatchSpectator(PlayerEntity viewer, GameWorldComponent gameComponent) {
        return gameComponent.isRunning()
                && gameComponent.isPlayerDead(viewer.getUuid())
                && (viewer.isSpectator() || viewer.isCreative());
    }
}
