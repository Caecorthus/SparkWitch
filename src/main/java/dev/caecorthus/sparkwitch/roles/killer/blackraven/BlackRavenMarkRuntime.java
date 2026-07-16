package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.UUID;
import net.minecraft.server.network.ServerPlayerEntity;

/** Settles each delayed Feather Blade mark exactly once. */
public final class BlackRavenMarkRuntime {
    private BlackRavenMarkRuntime() {
    }

    public static void tick(ServerPlayerEntity victim, BlackRavenMarkPlayerComponent component) {
        if (!component.hasMark()) {
            return;
        }
        UUID currentMatch = BlackRavenMatch.currentId();
        if (currentMatch == null || !currentMatch.equals(component.matchUuid())
                || !GameFunctions.isPlayerPlayingAndAlive(victim)) {
            component.clear();
            return;
        }
        if (victim.getServerWorld().getTime() < component.expiryTick()) {
            return;
        }

        UUID markerUuid = component.markerUuid();
        component.clear();
        ServerPlayerEntity killer = markerUuid == null || victim.getServer() == null
                ? null
                : victim.getServer().getPlayerManager().getPlayer(markerUuid);
        GameFunctions.killPlayer(victim, true, killer, GameConstants.DeathReasons.KNIFE);
    }
}
