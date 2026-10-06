package dev.caecorthus.sparkwitch.roles.civilian.saint;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Stores round-scoped Karma by player UUID; Grand Witch assignment explicitly removes the entry.
 * 按玩家 UUID 保存本局业障；成为大魔女时会显式移除对应条目。
 */
public final class SaintKarmaState {
    private final LinkedHashMap<UUID, Integer> remainingTicks = new LinkedHashMap<>();
    /**
     * Items an admin cleared during the running Karma (SparkFactionAPI {@code clearCooldown}); transient, never saved.
     * Dropped when the Karma ends, is triggered again (a new penalty covers every item), or is unmarked.
     * 业障进行中被管理员清除冷却的物品（SparkFactionAPI {@code clearCooldown}）；仅在内存中，从不保存。业障结束、再次触发
     * （新的惩罚覆盖所有物品）或取消标记时丢弃。
     */
    private final Map<UUID, Set<Identifier>> adminClearedItems = new HashMap<>();

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
        adminClearedItems.remove(playerUuid);
        return merged;
    }

    /** Only while Karma is running; returns whether the item was newly exempted. / 仅在业障进行中；返回是否新豁免。 */
    public boolean exemptAdminCleared(UUID playerUuid, Identifier itemId) {
        if (remainingTicks(playerUuid) <= 0) {
            return false;
        }
        return adminClearedItems.computeIfAbsent(playerUuid, ignored -> new HashSet<>()).add(itemId);
    }

    public boolean isAdminCleared(UUID playerUuid, Identifier itemId) {
        Set<Identifier> items = adminClearedItems.get(playerUuid);
        return items != null && items.contains(itemId);
    }

    public boolean unmark(UUID playerUuid) {
        adminClearedItems.remove(playerUuid);
        return remainingTicks.remove(playerUuid) != null;
    }

    public void tick() {
        remainingTicks.replaceAll((playerUuid, ticks) -> Math.max(0, ticks - 1));
        adminClearedItems.keySet().removeIf(playerUuid -> remainingTicks(playerUuid) <= 0);
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
        adminClearedItems.clear();
    }

    public record Entry(UUID playerUuid, int remainingTicks) {
    }
}
