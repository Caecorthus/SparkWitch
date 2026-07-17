package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.compat.SparkTraitsWraithBridge;
import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owns pre-death capture and post-death confirmation queueing only. */
public final class WraithDeathService {
    private static final Map<UUID, WraithDeathSnapshot> CAPTURED = new HashMap<>();

    private WraithDeathService() {
    }

    public static void captureBeforeMutation(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        UUID uuid = victim.getUuid();
        WraithPlayerComponent wraith = WraithPlayerComponent.KEY.get(victim);
        GameWorldComponent game = GameWorldComponent.KEY.get(victim.getWorld());
        Role role = game.getRole(victim);
        if (wraith.isActive() || role == null || !WraithRules.isEligibleDeath(role.getFaction(), deathReason)) {
            CAPTURED.remove(uuid);
            return;
        }

        try {
            CAPTURED.put(uuid, new WraithDeathSnapshot(
                    role.identifier(),
                    role.getFaction(),
                    WraithTaskSnapshot.capture(victim),
                    SparkTraitsWraithBridge.capture(victim),
                    WraithRules.alignmentFor(SparkFactionApi.resolveEffectiveFaction(victim, game)),
                    SparkTraitsWraithBridge.hasLastStandTriggered(victim.getServerWorld(), uuid),
                    (int) victim.getServerWorld().getTime()
            ));
        } catch (IllegalArgumentException ignored) {
            CAPTURED.remove(uuid);
        }
    }

    public static void afterKill(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        WraithDeathSnapshot snapshot = CAPTURED.remove(victim.getUuid());
        if (snapshot != null) {
            WraithDeferredActivationService.queueConfirmedDeath(victim, deathReason, snapshot);
        }
    }

    static void clearPlayer(ServerPlayerEntity player) {
        CAPTURED.remove(player.getUuid());
        WraithDeferredActivationService.clearPlayer(player);
    }

    static void clearAll() {
        CAPTURED.clear();
        WraithDeferredActivationService.clearAll();
    }
}
