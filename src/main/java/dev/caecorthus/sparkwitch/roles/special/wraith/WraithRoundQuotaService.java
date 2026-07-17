package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkwitch.component.WraithRoundComponent;
import net.minecraft.server.world.ServerWorld;

import java.util.UUID;

/** Coordinates round initialization and cumulative Wraith conversion slots. */
public final class WraithRoundQuotaService {
    private WraithRoundQuotaService() {
    }

    public static void beginRound(ServerWorld world, int startingPlayerCount) {
        WraithRoundComponent.KEY.get(world).beginRound(startingPlayerCount);
    }

    public static boolean hasCapacity(ServerWorld world) {
        return WraithRoundComponent.KEY.get(world).hasCapacity();
    }

    public static boolean tryConsume(ServerWorld world, UUID playerUuid) {
        return WraithRoundComponent.KEY.get(world).tryConsume(playerUuid);
    }

    public static void clearRound(ServerWorld world) {
        WraithRoundComponent.KEY.get(world).clearRound();
    }
}
