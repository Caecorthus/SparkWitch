package dev.caecorthus.sparkwitch.roles.civilian.tarotreader;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkwitch.SparkWitchFactions;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class TarotReaderRules {
    public static final int COLOR = 0xAEE1CF;
    public static final int INITIAL_MONEY = 0;
    public static final int TASK_MONEY_REWARD = 50;
    public static final int REGULAR_PRICE = 200;
    public static final int IDENTITY_PRICE = 50;
    public static final int SURVIVAL_PRICE = 50;

    private TarotReaderRules() {
    }

    public static boolean isTarotReader(@Nullable Role role) {
        return role == SparkWitchRoles.tarotReader();
    }

    static FactionBucket classifyFaction(Identifier effectiveFaction, Faction nativeFaction) {
        if (effectiveFaction == null || FactionIds.NONE.equals(effectiveFaction)) {
            return null;
        }
        if (SparkWitchFactions.WITCH.equals(effectiveFaction)) {
            return FactionBucket.WITCH;
        }
        if (FactionIds.CIVILIAN.equals(effectiveFaction)) {
            return FactionBucket.CIVILIAN;
        }
        if (FactionIds.KILLER.equals(effectiveFaction)) {
            return FactionBucket.KILLER;
        }
        if (FactionIds.NEUTRAL.equals(effectiveFaction)) {
            return FactionBucket.NEUTRAL;
        }
        if (nativeFaction == null) {
            return null;
        }
        return switch (nativeFaction) {
            case CIVILIAN -> FactionBucket.CIVILIAN;
            case KILLER -> FactionBucket.KILLER;
            case NEUTRAL -> FactionBucket.NEUTRAL;
            case NONE -> null;
        };
    }

    static boolean shouldCountActivePlayer(boolean assigned, boolean dead, boolean creative) {
        return assigned && !dead && !creative;
    }

    static boolean identityWasAssigned(boolean recordedInHistory, int currentAssignedCount) {
        return recordedInHistory || currentAssignedCount > 0;
    }

    static boolean isTargetAlive(boolean assigned, boolean dead) {
        return assigned && !dead;
    }

    enum FactionBucket {
        CIVILIAN,
        KILLER,
        NEUTRAL,
        WITCH
    }
}
