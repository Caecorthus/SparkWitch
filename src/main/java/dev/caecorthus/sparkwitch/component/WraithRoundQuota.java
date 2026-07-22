package dev.caecorthus.sparkwitch.component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Pure cumulative quota state used by the Wraith round component. */
final class WraithRoundQuota {
    private final LinkedHashSet<UUID> consumedPlayers = new LinkedHashSet<>();
    private int startingPlayerCount;

    private int minimum = 10;
    private int dividend = 5;

    static int capForStartingPlayers(int startingPlayerCount) {
        return capForStartingPlayers(startingPlayerCount, 10, 5);
    }

    static int capForStartingPlayers(int startingPlayerCount, int minimum, int dividend) {
        return startingPlayerCount < Math.max(0, minimum)
                ? 0
                : Math.max(0, startingPlayerCount) / Math.max(1, dividend);
    }

    void beginRound(int startingPlayerCount) {
        beginRound(startingPlayerCount, 10, 5);
    }

    void beginRound(int startingPlayerCount, int minimum, int dividend) {
        restore(startingPlayerCount, minimum, dividend, Set.of());
    }

    void restore(int startingPlayerCount, Collection<UUID> consumedPlayers) {
        restore(startingPlayerCount, 10, 5, consumedPlayers);
    }

    void restore(int startingPlayerCount, int minimum, int dividend, Collection<UUID> consumedPlayers) {
        this.startingPlayerCount = Math.max(0, startingPlayerCount);
        this.minimum = Math.max(0, minimum);
        this.dividend = Math.max(1, dividend);
        this.consumedPlayers.clear();
        this.consumedPlayers.addAll(consumedPlayers);
    }

    int getStartingPlayerCount() {
        return startingPlayerCount;
    }

    int getCap() {
        return capForStartingPlayers(startingPlayerCount, minimum, dividend);
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
        restore(0, 10, 5, Set.of());
    }
}
