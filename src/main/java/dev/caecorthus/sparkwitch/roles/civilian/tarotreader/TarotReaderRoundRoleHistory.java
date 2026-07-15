package dev.caecorthus.sparkwitch.roles.civilian.tarotreader;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Set;

/**
 * Keeps server-only role appearance history for the current round, including later role changes.
 * 仅在服务端记录本局出现过的职业，并保留中途转职前的历史。
 */
final class TarotReaderRoundRoleHistory {
    private static final Set<Identifier> ASSIGNED_ROLE_IDS = new HashSet<>();

    private TarotReaderRoundRoleHistory() {
    }

    static synchronized void record(Role role) {
        if (role != null) {
            ASSIGNED_ROLE_IDS.add(role.identifier());
        }
    }

    static synchronized boolean wasAssigned(Role role) {
        return role != null && ASSIGNED_ROLE_IDS.contains(role.identifier());
    }

    static synchronized void clear() {
        ASSIGNED_ROLE_IDS.clear();
    }
}
