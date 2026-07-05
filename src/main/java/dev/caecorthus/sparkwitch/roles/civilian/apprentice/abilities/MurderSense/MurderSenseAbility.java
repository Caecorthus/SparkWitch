package dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MurderSense;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.ApprenticeAbilitySupport;
import dev.doctor4t.wathe.game.GameConstants;
import java.util.Set;
import net.minecraft.util.Identifier;

public final class MurderSenseAbility {
    public static final Identifier ID = SparkWitch.id("murder_sense");
    public static final int MANA_COST = 60;
    public static final int DURATION_TICKS = GameConstants.getInTicks(0, 15);
    public static final int COOLDOWN_TICKS = GameConstants.getInTicks(1, 0);
    public static final double RANGE_BLOCKS = 20.0;
    public static final int COLOR = 0xFF3030;

    public static final Set<Identifier> DANGEROUS_ITEM_IDS = Set.of(
            Identifier.of("wathe", "revolver"),
            Identifier.of("wathe", "derringer"),
            Identifier.of("noellesroles", "demon_hunter_pistol"),
            Identifier.of("wathe", "knife"),
            Identifier.of("wathe", "bat"),
            Identifier.of("wathe", "grenade"),
            Identifier.of("wathe", "poison_vial"),
            Identifier.of("wathe", "scorpion"),
            Identifier.of("noellesroles", "poison_needle"),
            Identifier.of("noellesroles", "poison_gas_bomb"),
            Identifier.of("noellesroles", "throwing_axe"),
            SparkWitch.id("ceremonial_sword"),
            SparkWitch.id("fire_poker")
    );

    private MurderSenseAbility() {
    }

    public static WitchSkillUseResult use(WitchSkillUseContext context) {
        return ApprenticeAbilitySupport.use(
                context,
                MANA_COST,
                COOLDOWN_TICKS,
                "message.sparkwitch.skill.murder_sense.activated",
                () -> WitchPlayerComponent.KEY.get(context.player()).beginMurderSense(DURATION_TICKS)
        );
    }
}
