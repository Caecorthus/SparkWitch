package dev.caecorthus.sparkwitch.roles.special.wraith;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/** Coordinates the focused services that implement the Wraith lifecycle. */
public final class WraithService {
    private static boolean registered;

    private WraithService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        WraithTaskService.register();
        WraithPromotionService.register();
        WraithSessionService.register();
        WraithRuntimeService.register();
        WraithPlayerIsolationService.register();
        WraithInteractionService.register();
        WraithDeadPlayerParticipationService.register();
        WraithFactionService.register();
        WraithDeferredActivationService.register();
    }

    public static void clearPlayer(ServerPlayerEntity player) {
        WraithSessionService.clearPlayer(player);
    }

    public static void clearRoundState(ServerWorld world) {
        WraithSessionService.clearRoundState(world);
    }
}
