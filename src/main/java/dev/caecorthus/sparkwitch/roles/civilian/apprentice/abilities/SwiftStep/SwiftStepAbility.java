package dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.SwiftStep;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.ApprenticePlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.ApprenticeAbilitySupport;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Identifier;

public final class SwiftStepAbility {
    public static final Identifier ID = SparkWitch.id("swift_step");
    public static final int COLOR = 0x9CF26F;
    // Two charges, 20 mana each; every spent charge recharges on its own 20 s timer (owner D6).
    // 两层充能，每层 20 魔力；每层消耗后各自独立计时 20 秒恢复（所有者 D6）。
    public static final int MANA_COST = 20;
    public static final int DURATION_TICKS = GameConstants.getInTicks(0, 5);
    public static final int RECHARGE_TICKS = GameConstants.getInTicks(0, 20);
    // The charges own the pacing, so the shared skill cooldown has no registered floor.
    // 节奏由充能决定，因此共享技能冷却不注册下限。
    public static final int COOLDOWN_TICKS = 0;
    public static final int AMPLIFIER = 2;

    private SwiftStepAbility() {
    }

    public static WitchSkillUseResult use(WitchSkillUseContext context) {
        ApprenticePlayerComponent apprentice = ApprenticePlayerComponent.KEY.get(context.player());
        if (apprentice.getSwiftStepCharges() <= 0) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.swift_step.no_charge");
        }
        return ApprenticeAbilitySupport.useCharged(
                context,
                MANA_COST,
                "message.sparkwitch.skill.swift_step.activated",
                () -> {
                    apprentice.consumeSwiftStepCharge(RECHARGE_TICKS);
                    WitchPlayerComponent.KEY.get(context.player()).beginSwiftStep(DURATION_TICKS);
                    context.player().addStatusEffect(new StatusEffectInstance(
                            StatusEffects.SPEED,
                            DURATION_TICKS,
                            AMPLIFIER,
                            false,
                            false,
                            true
                    ));
                },
                apprentice::getTicksUntilSwiftStepCharge
        );
    }
}
