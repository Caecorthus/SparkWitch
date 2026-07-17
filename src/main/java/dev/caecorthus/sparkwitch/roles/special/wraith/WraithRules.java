package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;

/** Pure death-conversion rules for Wraith. */
public final class WraithRules {
    public static final double CONVERSION_CHANCE = 0.75D;

    private WraithRules() {
    }

    public static int randomCap(int startingPlayerCount) {
        return startingPlayerCount < 10 ? 0 : 1 + (startingPlayerCount - 10) / 5;
    }

    public static boolean passesChance(double randomRoll) {
        return randomRoll < CONVERSION_CHANCE;
    }

    public static boolean isEligibleDeath(Faction originalFaction, Identifier deathReason) {
        return (originalFaction == Faction.CIVILIAN || originalFaction == Faction.KILLER)
                && deathReason != null
                && !GameConstants.DeathReasons.ESCAPED.equals(deathReason)
                && !GameConstants.DeathReasons.FELL_OUT_OF_TRAIN.equals(deathReason);
    }

    public static WraithState.Alignment alignmentFor(Identifier effectiveFaction) {
        if (FactionIds.KILLER.equals(effectiveFaction)) {
            return WraithState.Alignment.KILLER;
        }
        if (FactionIds.CIVILIAN.equals(effectiveFaction)) {
            return WraithState.Alignment.GOOD;
        }
        throw new IllegalArgumentException("Wraith requires an effective GOOD or KILLER alignment");
    }
}
