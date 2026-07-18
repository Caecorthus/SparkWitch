package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithRules;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Pure cumulative quota state used by the Wraith round component. */
final class WraithRoundQuota {
    private final LinkedHashSet<UUID> consumedPlayers = new LinkedHashSet<>();
    private int startingPlayerCount;

    void beginRound(int startingPlayerCount) {
        restore(startingPlayerCount, Set.of());
    }

    void restore(int startingPlayerCount, Collection<UUID> consumedPlayers) {
        this.startingPlayerCount = Math.max(0, startingPlayerCount);
        this.consumedPlayers.clear();
        this.consumedPlayers.addAll(consumedPlayers);
    }

    int getStartingPlayerCount() {
        return startingPlayerCount;
    }

    int getCap() {
        return WraithRules.randomCap(startingPlayerCount);
    }

    int getConsumedCount() {
        return consumedPlayers.size();
    }

    Set<UUID> getConsumedPlayers() {
        return Set.copyOf(consumedPlayers);
    }

    boolean hasCapacity() {
        return consumedPlayers.size() < getCap();
    }

    boolean tryConsume(UUID playerUuid) {
        if (playerUuid == null || consumedPlayers.contains(playerUuid) || !hasCapacity()) {
            return false;
        }
        return consumedPlayers.add(playerUuid);
    }

    void clearRound() {
        restore(0, Set.of());
    }
}
