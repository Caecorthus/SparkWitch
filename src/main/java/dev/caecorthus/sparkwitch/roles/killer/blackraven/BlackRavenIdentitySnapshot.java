package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable identity captured at the instant Perception reaches ten points.
 * 感知达到十点瞬间冻结的不可变身份快照。
 */
public record BlackRavenIdentitySnapshot(UUID targetUuid, String playerName, String roleId, int roleColor) {
    public BlackRavenIdentitySnapshot {
        Objects.requireNonNull(targetUuid, "targetUuid");
        playerName = Objects.requireNonNullElse(playerName, "");
        roleId = Objects.requireNonNullElse(roleId, "");
    }

    public UUID playerId() {
        return targetUuid;
    }

    public String roleTranslationKey() {
        int separator = roleId.indexOf(':');
        String path = separator >= 0 ? roleId.substring(separator + 1) : roleId;
        return "announcement.role." + path;
    }
}
