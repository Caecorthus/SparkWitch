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

    public static boolean isSparkWitchRole(Role role) {
        return SparkWitchRoleRegistry.isSparkWitchRole(role);
    }
}
