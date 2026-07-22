package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;

/** Pure death-conversion rules for Wraith. */
public final class WraithRules {
    private WraithRules() {
    }

    public static boolean passesChance(double randomRoll) {
        return passesChance(randomRoll, WraithSettings.DEFAULT_CHANCE);
    }

    public static boolean passesChance(double randomRoll, int chancePercent) {
        int normalizedChance = Math.max(0, Math.min(100, chancePercent));
        return randomRoll < normalizedChance / 100.0D;
    }

    public static boolean isEligibleDeath(Faction originalFaction, Identifier deathReason) {
        return isEligibleDeath(originalFaction, deathReason, false);
    }

    public static boolean isEligibleDeath(
            Faction originalFaction,
            Identifier deathReason,
            boolean attributedFall
    ) {
        return (originalFaction == Faction.CIVILIAN || originalFaction == Faction.KILLER)
                && isEligibleDeathReason(deathReason, attributedFall);
    }

    /** Custom factions are validated from the effective-faction alignment snapshot. */
    public static boolean isEligibleDeath(
            WraithState.Alignment alignment,
            Identifier deathReason,
            boolean attributedFall
    ) {
        return alignment != null && isEligibleDeathReason(deathReason, attributedFall);
    }

    private static boolean isEligibleDeathReason(Identifier deathReason, boolean attributedFall) {
        return deathReason != null
                && !GameConstants.DeathReasons.ESCAPED.equals(deathReason)
                && (attributedFall || !GameConstants.DeathReasons.FELL_OUT_OF_TRAIN.equals(deathReason));
    }

    public static WraithState.Alignment alignmentFor(Identifier effectiveFaction) {
        if (FactionIds.KILLER.equals(effectiveFaction)) {
            return WraithState.Alignment.KILLER;
        }
        if (FactionIds.CIVILIAN.equals(effectiveFaction)) {
            return WraithState.Alignment.GOOD;
        }
        if (Identifier.of("sparkwitch", "witch").equals(effectiveFaction)) {
            return WraithState.Alignment.WITCH;
        }
        throw new IllegalArgumentException("Wraith requires an effective GOOD, KILLER, or WITCH alignment");
    }
}
