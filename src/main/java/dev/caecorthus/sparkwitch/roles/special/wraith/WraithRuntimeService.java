package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

/** Maintains active Wraith effects and below-train termination. */
final class WraithRuntimeService {
    private static boolean registered;

    private WraithRuntimeService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_WORLD_TICK.register(WraithRuntimeService::tickWorld);
    }

    private static void tickWorld(ServerWorld world) {
        Box playArea = MapVariablesWorldComponent.KEY.get(world).getPlayArea();
        for (ServerPlayerEntity player : world.getPlayers()) {
            WraithPlayerComponent wraith = WraithPlayerComponent.KEY.get(player);
            if (!wraith.isActive()) {
                continue;
            }
            if (playArea != null
                    && WraithLifecycleRules.shouldTerminateForFall(true, player.getY(), playArea.minY)) {
                WraithSessionService.terminateAsSpectator(player);
                continue;
            }
            WraithRuntimeStateService.apply(player, wraith.isRestricted());
        }
    }
}
