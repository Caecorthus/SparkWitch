package dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MightyForce;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.ApprenticeAbilitySupport;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;

public final class MightyForceAbility {
    public static final Identifier ID = SparkWitch.id("mighty_force");
    public static final int COLOR = 0x75EDFA;
    public static final int MANA_COST = 80;
    public static final int WINDOW_TICKS = GameConstants.getInTicks(0, 10);
    public static final int COOLDOWN_TICKS = GameConstants.getInTicks(1, 0);
    public static final double KNOCKBACK_STRENGTH = 10.0;

    private MightyForceAbility() {
    }

    public static WitchSkillUseResult use(WitchSkillUseContext context) {
        return ApprenticeAbilitySupport.use(
                context,
                MANA_COST,
                COOLDOWN_TICKS,
                "message.sparkwitch.skill.mighty_force.activated",
                () -> WitchPlayerComponent.KEY.get(context.player()).beginMightyForceWindow(WINDOW_TICKS)
        );
    }
}
