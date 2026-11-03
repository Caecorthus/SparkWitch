package dev.caecorthus.sparkwitch.roles.undercover;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.component.UndercoverDisguiseComponent;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Gives every undercover a random killer identity for the round, independent of whether a consultant is present.
 * <p>The disguise is what the consultant's instinct shows, and what the undercover sees under the round timer.
 * 每局都给卧底一个随机杀手身份（与是否存在顾问无关）。这个假身份既供顾问的透视使用，也显示在卧底的时间下方。
 */
public final class UndercoverDisguiseService {

    /** NoellesRoles' undercover role id. 诺艾尔模组的卧底身份 id。 */
    public static final Identifier UNDERCOVER_ID = Identifier.of("noellesroles", "undercover");

    /** Killer identities an undercover may be disguised as. 卧底可能伪装成的杀手身份。 */
    private static final List<Identifier> KILLER_IDENTITIES = List.of(
            // SparkWitch killers. SparkWitch 杀手。
            SparkWitchRoles.NINJA_ID,
            SparkWitchRoles.HUNTER_ID,
            SparkWitchRoles.KIDNAPPER_ID,
            SparkWitchRoles.BELL_RINGER_ID,
            SparkWitchRoles.TIME_STEALER_ID,
            SparkWitchRoles.MAGICIAN_ID,
            SparkWitchRoles.WITCH_MAIDEN_ID,
            SparkWitchRoles.CONSULTANT_ID,
            // NoellesRoles killers. 诺艾尔模组杀手。
            Identifier.of("noellesroles", "phantom"),
            Identifier.of("noellesroles", "swapper"),
            Identifier.of("noellesroles", "the_insane_damned_paranoid_killer"),
            Identifier.of("noellesroles", "morphling"),
            Identifier.of("noellesroles", "assassin"),
            Identifier.of("noellesroles", "scavenger"),
            Identifier.of("noellesroles", "bomber"),
            Identifier.of("noellesroles", "serial_killer"),
            Identifier.of("noellesroles", "silencer"),
            Identifier.of("noellesroles", "party_animal"),
            Identifier.of("noellesroles", "poisoner"),
            Identifier.of("noellesroles", "bandit"),
            // Wathe's own killers. 原版杀手。
            Identifier.of("wathe", "killer"),
            Identifier.of("wathe", "secret_killer")
    );

    private UndercoverDisguiseService() {
    }

    public static void register() {
        SparkWitch.LOGGER.info("Registering undercover disguise hook for {}", UNDERCOVER_ID);
        RoleAssigned.EVENT.register((player, role) -> {
            if (role == null || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }
            if (!UNDERCOVER_ID.equals(role.identifier())) {
                return;
            }
            Identifier disguise = KILLER_IDENTITIES.get(serverPlayer.getRandom().nextInt(KILLER_IDENTITIES.size()));
            UndercoverDisguiseComponent.KEY.get(serverPlayer).setDisguise(disguise);
        });
    }
}
