package dev.caecorthus.sparkwitch.roles.special.wraith;

import net.minecraft.server.network.ServerPlayerEntity;
import org.agmas.noellesroles.silencer.SilencedPlayerComponent;

/** Applies properties that must hold throughout active Wraith play. */
final class WraithRuntimeStateService {
    private WraithRuntimeStateService() {
    }

    static void apply(ServerPlayerEntity player, boolean restricted) {
        player.setInvulnerable(true);
        SilencedPlayerComponent.KEY.get(player).reset();
        WraithEffectService.apply(player, restricted);
    }

    static void clear(ServerPlayerEntity player) {
        WraithEffectService.removeAll(player);
        player.setInvulnerable(false);
    }
}
