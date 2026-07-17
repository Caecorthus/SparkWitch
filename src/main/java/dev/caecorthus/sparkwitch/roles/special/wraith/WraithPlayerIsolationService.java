package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;

/** Registers the bilateral active-Wraith player-affect veto. */
final class WraithPlayerIsolationService {
    private static boolean registered;

    private WraithPlayerIsolationService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        SparkFactionApi.registerPlayerAffectPolicy((actor, target, actionId, gameComponent) ->
                WraithLifecycleRules.canAffectPlayer(
                        WraithStateService.isActive(actor),
                        WraithStateService.isActive(target),
                        actor.getUuid().equals(target.getUuid())
                ));
    }
}
