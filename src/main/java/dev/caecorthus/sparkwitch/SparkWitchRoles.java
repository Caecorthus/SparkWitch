package dev.caecorthus.sparkwitch;

import dev.caecorthus.sparkwitch.registry.SparkWitchRoleRegistry;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;

public final class SparkWitchRoles {
    public static final Identifier GRAND_WITCH_ID = SparkWitchRoleRegistry.GRAND_WITCH_ID;
    public static final Identifier ACCOMPLICE_ID = SparkWitchRoleRegistry.ACCOMPLICE_ID;
    public static final Identifier APPRENTICE_WITCH_ID = SparkWitchRoleRegistry.APPRENTICE_WITCH_ID;
    public static final Identifier MURDEROUS_WITCH_ID = SparkWitchRoleRegistry.MURDEROUS_WITCH_ID;
    public static final Identifier PIG_GOD_ID = SparkWitchRoleRegistry.PIG_GOD_ID;
    public static final Identifier SAINT_ID = SparkWitchRoleRegistry.SAINT_ID;
    public static final Identifier PERFUMER_ID = SparkWitchRoleRegistry.PERFUMER_ID;
    public static final Identifier NINJA_ID = SparkWitchRoleRegistry.NINJA_ID;
    public static final Identifier HUNTER_ID = SparkWitchRoleRegistry.HUNTER_ID;
    public static final Identifier ORTHOPEDIST_ID = SparkWitchRoleRegistry.ORTHOPEDIST_ID;
    public static final Identifier KIDNAPPER_ID = SparkWitchRoleRegistry.KIDNAPPER_ID;
    public static final Identifier TAROT_READER_ID = SparkWitchRoleRegistry.TAROT_READER_ID;

    private SparkWitchRoles() {
    }

    public static synchronized void register() {
        SparkWitchRoleRegistry.register();
    }

    public static synchronized void refreshAssassinGuessRoleOrder() {
        SparkWitchRoleRegistry.refreshAssassinGuessRoleOrder();
    }

    public static Role grandWitch() {
        return SparkWitchRoleRegistry.grandWitch();
    }

    public static Role accomplice() {
        return SparkWitchRoleRegistry.accomplice();
    }

    public static Role apprenticeWitch() {
        return SparkWitchRoleRegistry.apprenticeWitch();
    }

    public static Role murderousWitch() {
        return SparkWitchRoleRegistry.murderousWitch();
    }

    public static Role pigGod() {
        return SparkWitchRoleRegistry.pigGod();
    }

    public static Role saint() {
        return SparkWitchRoleRegistry.saint();
    }

    public static Role perfumer() {
        return SparkWitchRoleRegistry.perfumer();
    }

    public static Role ninja() {
        return SparkWitchRoleRegistry.ninja();
    }

    public static Role hunter() {
        return SparkWitchRoleRegistry.hunter();
    }

    public static Role orthopedist() {
        return SparkWitchRoleRegistry.orthopedist();
    }

    public static Role kidnapper() {
        return SparkWitchRoleRegistry.kidnapper();
    }

    public static Role tarotReader() {
        return SparkWitchRoleRegistry.tarotReader();
    }

    public static boolean isSparkWitchRole(Role role) {
        return SparkWitchRoleRegistry.isSparkWitchRole(role);
    }
}
