package dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.Clairvoyance;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.ApprenticeAbilitySupport;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;

public final class ClairvoyanceAbility {
    public static final Identifier ID = SparkWitch.id("clairvoyance");
    public static final int MANA_COST = 80;
    public static final int SELF_TICKS = GameConstants.getInTicks(0, 30);
    public static final int OTHERS_TICKS = GameConstants.getInTicks(0, 10);
    public static final int COOLDOWN_TICKS = GameConstants.getInTicks(1, 0);
    public static final int SELF_COLOR = 0x7EE8FF;
    public static final int TARGET_COLOR = 0xFFFFFF;

    private ClairvoyanceAbility() {
    }

    public static WitchSkillUseResult use(WitchSkillUseContext context) {
        return ApprenticeAbilitySupport.use(
                context,
                MANA_COST,
                COOLDOWN_TICKS,
                "message.sparkwitch.skill.clairvoyance.activated",
                () -> WitchPlayerComponent.KEY.get(context.player()).beginClairvoyance(SELF_TICKS, OTHERS_TICKS)
        );
    }
}
