package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.Role;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Non-rollable role identities awarded after Wraith promotion.
 * 冤魂晋升后获得的不可随机抽取身份。
 */
public final class WraithPromotionRoles {
    private WraithPromotionRoles() {
    }

    public static List<Role> pool(WraithState.Alignment alignment) {
        return switch (alignment) {
            case GOOD -> List.of(
                    SparkWitchRoles.windSpirit(),
                    SparkWitchRoles.guardianAngel(),
                    SparkWitchRoles.vendetta()
            );
            case KILLER -> List.of(
                    SparkWitchRoles.saboteur(),
                    SparkWitchRoles.curser()
            );
        };
    }

    public static Role pick(WraithState.Alignment alignment, RandomGenerator random) {
        List<Role> pool = pool(alignment);
        return pool.get(random.nextInt(pool.size()));
    }

    /**
     * Preserves NoellesRoles' native-killer exclusion except for the promoted Saboteur identity.
     * 保留 NoellesRoles 的原生杀手过滤，仅对晋升后的破坏者放行。
     */
    public static boolean shouldExcludeFromAssassinGuess(Role role) {
        return role.canUseKiller() && !SparkWitchRoles.SABOTEUR_ID.equals(role.identifier());
    }
}
