package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

import java.util.UUID;

/** Pure server-authoritative bond, promotion, and timer rules for Vendetta. */
public final class VendettaRules {
    public static final int REVEAL_COOLDOWN_TICKS = 30 * 20;
    public static final int REVEAL_DURATION_TICKS = 5 * 20;
    public static final int BOUND_KILLER_RECONNECT_GRACE_TICKS = 30 * 20;
    public static final int TERMINAL_MONEY_REWARD = 200;
    public static final int TERMINAL_MANA_REWARD = 200;

    private VendettaRules() {
    }

    public static boolean isExactPair(
            UUID actorUuid,
            UUID targetUuid,
            UUID vendettaUuid,
            UUID killerUuid,
            boolean active
    ) {
        return active && killerUuid != null && (
                actorUuid.equals(vendettaUuid) && targetUuid.equals(killerUuid)
                        || actorUuid.equals(killerUuid) && targetUuid.equals(vendettaUuid)
        );
    }

    public static boolean canPromote(
            boolean hasCreditedKiller,
            boolean killerHasRole,
            boolean killerDead,
            boolean selfBond
    ) {
        return hasCreditedKiller && killerHasRole && !killerDead && !selfBond;
    }

    static boolean hasReconnectGraceExpired(long disconnectedAtTick, long currentTick) {
        return currentTick - disconnectedAtTick >= BOUND_KILLER_RECONNECT_GRACE_TICKS;
    }
}
