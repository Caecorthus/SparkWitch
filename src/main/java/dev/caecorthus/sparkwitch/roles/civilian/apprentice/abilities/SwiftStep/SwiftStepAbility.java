package dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.SwiftStep;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.ApprenticeAbilitySupport;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Identifier;

public final class SwiftStepAbility {
    public static final Identifier ID = SparkWitch.id("swift_step");
    public static final int COLOR = 0x9CF26F;
    public static final int MANA_COST = 30;
    public static final int DURATION_TICKS = GameConstants.getInTicks(0, 5);
    public static final int COOLDOWN_TICKS = GameConstants.getInTicks(0, 30);
    public static final int AMPLIFIER = 2;

    private SwiftStepAbility() {
    }

    public static WitchSkillUseResult use(WitchSkillUseContext context) {
        return ApprenticeAbilitySupport.use(
                context,
                MANA_COST,
                COOLDOWN_TICKS,
                "message.sparkwitch.skill.swift_step.activated",
                () -> {
                    WitchPlayerComponent.KEY.get(context.player()).beginSwiftStep(DURATION_TICKS);
                    context.player().addStatusEffect(new StatusEffectInstance(
                            StatusEffects.SPEED,
                            DURATION_TICKS,
                            AMPLIFIER,
                            false,
                            false,
                            true
                    ));
                }
        );
    }
}
