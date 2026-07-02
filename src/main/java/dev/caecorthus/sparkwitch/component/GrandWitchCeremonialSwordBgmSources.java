package dev.caecorthus.sparkwitch.component;

import java.util.LinkedHashSet;
import java.util.UUID;

/**
 * Tracks the players currently keeping the Grand Witch Ceremonial Sword BGM alive.
 * 记录当前维持大魔女仪礼剑 BGM 的玩家，避免多个窗口重叠时提前关闭全场环境音。
 */
final class GrandWitchCeremonialSwordBgmSources {
    private final LinkedHashSet<UUID> playerUuids = new LinkedHashSet<>();

    boolean start(UUID playerUuid) {
        return playerUuid != null && playerUuids.add(playerUuid);
    }

    boolean stop(UUID playerUuid) {
        return playerUuid != null && playerUuids.remove(playerUuid);
    }

    void clear() {
        playerUuids.clear();
    }

    boolean isActive() {
        return size() > 0;
    }

    int size() {
        return playerUuids.size();
    }
}
