package dev.caecorthus.sparkwitch.roles.civilian.saint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stores round-scoped Karma by player UUID; Grand Witch assignment explicitly removes the entry.
 * 按玩家 UUID 保存本局业障；成为大魔女时会显式移除对应条目。
 */
public final class SaintKarmaState {
    private final LinkedHashMap<UUID, Integer> remainingTicks = new LinkedHashMap<>();

    public boolean mark(UUID playerUuid) {
        if (remainingTicks.containsKey(playerUuid)) {
            return false;
        }
        remainingTicks.put(playerUuid, 0);
        return true;
    }

    public boolean isMarked(UUID playerUuid) {
        return remainingTicks.containsKey(playerUuid);
    }

    public int remainingTicks(UUID playerUuid) {
        return remainingTicks.getOrDefault(playerUuid, 0);
    }

    public int trigger(UUID playerUuid, int durationTicks) {
        if (!isMarked(playerUuid)) {
            return 0;
        }
        int merged = SaintRules.mergeCooldownTicks(remainingTicks(playerUuid), durationTicks);
        remainingTicks.put(playerUuid, merged);
        return merged;
    }

    public boolean unmark(UUID playerUuid) {
        return remainingTicks.remove(playerUuid) != null;
    }

    public void tick() {
        remainingTicks.replaceAll((playerUuid, ticks) -> Math.max(0, ticks - 1));
    }

    public void restore(UUID playerUuid, int ticks) {
        remainingTicks.put(playerUuid, Math.max(0, ticks));
    }

    public List<Entry> entries() {
        List<Entry> entries = new ArrayList<>(remainingTicks.size());
        for (Map.Entry<UUID, Integer> entry : remainingTicks.entrySet()) {
            entries.add(new Entry(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(entries);
    }

    public void clear() {
        remainingTicks.clear();
    }

    public record Entry(UUID playerUuid, int remainingTicks) {
    }
}
