package dev.caecorthus.sparkwitch.roles.civilian.judge;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

/** Round-owned exact causes, keyed by world identity and victim UUID. / 回合内的确切因果，按世界对象与受害者 UUID 隔离。 */
final class JudgeFallEpisodeLedger<W> {
    private final Map<W, Map<UUID, Episode>> worlds = new IdentityHashMap<>();

    void put(W world, UUID victim, UUID actor, boolean unsupportedSwap) {
        if (actor != null) worlds.computeIfAbsent(world, ignored -> new HashMap<>())
                .put(victim, new Episode(actor, unsupportedSwap));
    }

    Episode get(W world, UUID victim) {
        Map<UUID, Episode> episodes = worlds.get(world);
        return episodes == null ? null : episodes.get(victim);
    }

    Episode take(W world, UUID victim) {
        Map<UUID, Episode> episodes = worlds.get(world);
        if (episodes == null) return null;
        Episode result = episodes.remove(victim);
        if (episodes.isEmpty()) worlds.remove(world);
        return result;
    }

    void clearWorld(W world) {
        worlds.remove(world);
    }

    void clearVictim(UUID victim) {
        worlds.values().forEach(episodes -> episodes.remove(victim));
        worlds.values().removeIf(Map::isEmpty);
    }

    void prune(W world, BiPredicate<UUID, Episode> retain) {
        Map<UUID, Episode> episodes = worlds.get(world);
        if (episodes == null) return;
        episodes.entrySet().removeIf(entry -> !retain.test(entry.getKey(), entry.getValue()));
        if (episodes.isEmpty()) worlds.remove(world);
    }

    record Episode(UUID actor, boolean unsupportedSwap) {
        boolean remains(boolean playingAndAlive, boolean belowTrain, boolean safeSupport) {
            // Below the lethal boundary is not safe ground recovery, even if an exterior block supports the player.
            // 致死边界下方即使踩到外部方块也不算安全恢复。
            return playingAndAlive && (belowTrain || unsupportedSwap && !safeSupport);
        }
    }
}
