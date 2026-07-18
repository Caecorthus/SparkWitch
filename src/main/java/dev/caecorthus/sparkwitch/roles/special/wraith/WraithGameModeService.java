package dev.caecorthus.sparkwitch.roles.special.wraith;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;

/** Performs the one-time Spectator-to-Adventure transition at activation. */
final class WraithGameModeService {
    private WraithGameModeService() {
    }

    static void activate(ServerPlayerEntity player) {
        if (player.isSpectator()) {
            player.changeGameMode(GameMode.ADVENTURE);
        }
    }
}
