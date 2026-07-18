package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkwitch.SparkWitchFactions;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;

/**
 * Pure death-conversion and quota rules for Wraith.
 * 冤魂死亡转化与名额上限的纯规则。
 */
public final class WraithRules {
    public static final double CONVERSION_CHANCE = 0.75;
    public static final Identifier DIGESTED = Identifier.of("noellesroles", "digested");
    private static final int MIN_RANDOM_PLAYERS = 10;
    private static final int RANDOM_CAP_STEP_PLAYERS = 5;

    private WraithRules() {
    }

    public static int randomCap(int startingPlayerCount) {
        if (startingPlayerCount < MIN_RANDOM_PLAYERS) {
            return 0;
        }
        return 1 + (startingPlayerCount - MIN_RANDOM_PLAYERS) / RANDOM_CAP_STEP_PLAYERS;
    }

    public static boolean isEligibleFaction(Identifier factionId) {
        return FactionIds.CIVILIAN.equals(factionId)
                || FactionIds.KILLER.equals(factionId)
                || SparkWitchFactions.WITCH.equals(factionId);
    }

    public static boolean isEligibleDeath(
            Identifier originalFaction,
            Identifier deathReason,
            boolean pushedFall
    ) {
        if (!isEligibleFaction(originalFaction)
                || deathReason == null
                || GameConstants.DeathReasons.ESCAPED.equals(deathReason)
                || DIGESTED.equals(deathReason)) {
            return false;
        }
        return !GameConstants.DeathReasons.FELL_OUT_OF_TRAIN.equals(deathReason) || pushedFall;
    }

    public static boolean shouldBecomeWraith(
            Identifier originalFaction,
            Identifier deathReason,
            boolean pushedFall,
            double randomRoll
    ) {
        return isEligibleDeath(originalFaction, deathReason, pushedFall)
                && randomRoll < CONVERSION_CHANCE;
    }

    public static WraithState.Alignment effectiveAlignment(Identifier effectiveFaction) {
        if (FactionIds.CIVILIAN.equals(effectiveFaction)) {
            return WraithState.Alignment.CIVILIAN;
        }
        if (FactionIds.KILLER.equals(effectiveFaction)) {
            return WraithState.Alignment.KILLER;
        }
        if (SparkWitchFactions.WITCH.equals(effectiveFaction)) {
            return WraithState.Alignment.WITCH;
        }
        throw new IllegalArgumentException("Wraith requires an eligible effective faction: " + effectiveFaction);
    }

    public static boolean fallbackIsAboveTrain(double y, double playAreaMinY) {
        return y >= playAreaMinY;
    }
}
