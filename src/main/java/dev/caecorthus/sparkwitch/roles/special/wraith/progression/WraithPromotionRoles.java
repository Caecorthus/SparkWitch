package dev.caecorthus.sparkwitch.roles.special.wraith.progression;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithState;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.random.RandomGenerator;

final class WraithPromotionRoles {
    private WraithPromotionRoles() {
    }

    static List<Role> pool(WraithState.Alignment alignment) {
        return pool(alignment, true);
    }

    static List<Role> pool(WraithState.Alignment alignment, boolean vendettaEligible) {
        return switch (alignment) {
            case GOOD -> vendettaEligible
                    ? List.of(
                            SparkWitchRoles.windSpirit(),
                            SparkWitchRoles.guardianAngel(),
                            SparkWitchRoles.vendetta()
                    )
                    : List.of(
                            SparkWitchRoles.windSpirit(),
                            SparkWitchRoles.guardianAngel()
                    );
            case KILLER -> List.of(SparkWitchRoles.saboteur());
            case WITCH -> List.of(SparkWitchRoles.curser());
        };
    }

    static Role find(Identifier roleId) {
        if (SparkWitchRoles.WIND_SPIRIT_ID.equals(roleId)) {
            return SparkWitchRoles.windSpirit();
        }
        if (SparkWitchRoles.GUARDIAN_ANGEL_ID.equals(roleId)) {
            return SparkWitchRoles.guardianAngel();
        }
        if (SparkWitchRoles.VENDETTA_ID.equals(roleId)) {
            return SparkWitchRoles.vendetta();
        }
        if (SparkWitchRoles.SABOTEUR_ID.equals(roleId)) {
            return SparkWitchRoles.saboteur();
        }
        if (SparkWitchRoles.CURSER_ID.equals(roleId)) {
            return SparkWitchRoles.curser();
        }
        return null;
    }

    static Role pick(WraithState.Alignment alignment, RandomGenerator random) {
        return pick(alignment, true, random);
    }

    static Role pick(WraithState.Alignment alignment, boolean vendettaEligible, RandomGenerator random) {
        List<Role> pool = pool(alignment, vendettaEligible);
        return pool.get(random.nextInt(pool.size()));
    }

    static boolean shouldExcludeFromAssassinGuess(Role role) {
        return role.canUseKiller() && !SparkWitchRoles.SABOTEUR_ID.equals(role.identifier());
    }
}
