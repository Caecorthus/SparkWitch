package dev.caecorthus.sparkwitch.registry;

/**
 * Pure one-way spawn relationship: Hunter requires Orthopedist, but not vice versa.
 * 单向刷新关系：猎人必须带骨科大夫，骨科大夫可以独立出现。
 */
public final class HunterOrthopedistPairingRules {
    private HunterOrthopedistPairingRules() {
    }

    public static boolean canRandomHunterAppear(boolean orthopedistEnabled, int totalPlayers) {
        return orthopedistEnabled && totalPlayers >= 2;
    }

    public static boolean needsOrthopedist(boolean hunterPresent, int orthopedistCount) {
        return hunterPresent && orthopedistCount == 0;
    }

    public static PairingAction pairingAction(
            int hunterCount,
            int orthopedistCount,
            int availablePlayers,
            boolean orthopedistEnabled
    ) {
        if (!needsOrthopedist(hunterCount > 0, orthopedistCount)) {
            return PairingAction.NONE;
        }
        return orthopedistEnabled && availablePlayers > 0
                ? PairingAction.ASSIGN_ORTHOPEDIST
                : PairingAction.DEMOTE_HUNTERS;
    }

    public enum PairingAction {
        NONE,
        ASSIGN_ORTHOPEDIST,
        DEMOTE_HUNTERS
    }
}
