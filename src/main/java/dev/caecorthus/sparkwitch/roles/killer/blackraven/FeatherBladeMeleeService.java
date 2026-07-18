package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.api.event.AllowPlayerPunching;

/**
 * Allows Feather Blade through Wathe's player-only melee seam without enabling ordinary punches.
 * 仅放行羽刃进入 Wathe 的玩家近战接口，不开放普通空手攻击。
 */
public final class FeatherBladeMeleeService {
    private static boolean registered;

    private FeatherBladeMeleeService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        AllowPlayerPunching.EVENT.register((attacker, victim) ->
                attacker.getMainHandStack().isOf(SparkWitchItems.featherBlade())
                        && VendettaInteractionService.isOrdinaryAliveOrBoundKillerTarget(attacker, victim)
        );
    }
}
