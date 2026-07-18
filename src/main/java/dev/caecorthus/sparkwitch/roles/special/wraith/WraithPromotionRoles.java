package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkwitch.roles.civilian.guardianangel.GuardianAngelRole;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaRole;
import dev.caecorthus.sparkwitch.roles.civilian.windspirit.WindSpiritRole;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurRole;
import dev.caecorthus.sparkwitch.roles.witch.curser.CurserRole;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Canonical, non-rollable role identities awarded after Wraith promotion.
 * 冤魂升变后获得且不会参与开局抽取的标准身份。
 */
public final class WraithPromotionRoles {
    public static final Identifier WIND_SPIRIT_ID = WindSpiritRole.ID;
    public static final Identifier GUARDIAN_ANGEL_ID = GuardianAngelRole.ID;
    public static final Identifier VENDETTA_ID = VendettaRole.ID;
    public static final Identifier SABOTEUR_ID = SaboteurRole.ID;
    public static final Identifier CURSER_ID = CurserRole.ID;

    private WraithPromotionRoles() {
    }

    public static List<Role> pool(WraithState.Alignment alignment) {
        return switch (alignment) {
            case CIVILIAN -> List.of(
                    requireRegisteredRole(WIND_SPIRIT_ID),
                    requireRegisteredRole(GUARDIAN_ANGEL_ID),
                    requireRegisteredRole(VENDETTA_ID)
            );
            case KILLER -> List.of(requireRegisteredRole(SABOTEUR_ID));
            case WITCH -> List.of(requireRegisteredRole(CURSER_ID));
        };
    }

    public static Role pick(WraithState.Alignment alignment, RandomGenerator random) {
        List<Role> pool = pool(alignment);
        return pool.get(random.nextInt(pool.size()));
    }

    private static Role requireRegisteredRole(Identifier roleId) {
        Role role = WatheRoles.getRole(roleId);
        if (role == null) {
            throw new IllegalStateException("Wraith promotion role is not registered: " + roleId);
        }
        return role;
    }
}
