package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import java.util.Objects;
import java.util.UUID;

/**
 * Who was perceived, frozen at the instant Perception reaches ten points. Since the 2026-10-21 police rework it holds no
 * role: the ledger and the x-ray only know that a player was perceived.
 * 感知达到十点瞬间冻结的“被感知者”记录。自 2026-10-21 警察化起不再含职业：感知册与透视只知道某玩家已被感知。
 */
public record BlackRavenIdentitySnapshot(UUID targetUuid, String playerName) {
    public BlackRavenIdentitySnapshot {
        Objects.requireNonNull(targetUuid, "targetUuid");
        playerName = Objects.requireNonNullElse(playerName, "");
    }

    public UUID playerId() {
        return targetUuid;
    }
}
