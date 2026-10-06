package dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import java.util.function.IntSupplier;

public final class ApprenticeAbilitySupport {
    private ApprenticeAbilitySupport() {
    }

    /**
     * Shared Apprentice Witch activation guard; keeps role and mana checks out of each effect body.
     * 预备魔女能力共用的启用守卫；角色和魔力校验不散落到每个效果体里。
     */
    public static WitchSkillUseResult use(
            WitchSkillUseContext context,
            int manaCost,
            int cooldownTicks,
            String successMessageKey,
            Runnable effect
    ) {
        if (context.role() != SparkWitchRoles.apprenticeWitch()) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.unavailable");
        }
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(context.player());
        if (!component.spendMana(manaCost)) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.not_enough_mana");
        }

        effect.run();
        return WitchSkillUseResult.successAfterActiveWindow(cooldownTicks, successMessageKey);
    }

    /**
     * Charge-based variant (Swift Step, owner D6): the cooldown starts at once and is whatever {@code cooldownAfter}
     * reports after the effect ran, so a remaining charge leaves the skill ready.
     * 充能型变体（滑步，所有者 D6）：冷却立即开始，取效果执行后 {@code cooldownAfter} 给出的值，因此尚有充能时技能保持就绪。
     */
    public static WitchSkillUseResult useCharged(
            WitchSkillUseContext context,
            int manaCost,
            String successMessageKey,
            Runnable effect,
            IntSupplier cooldownAfter
    ) {
        if (context.role() != SparkWitchRoles.apprenticeWitch()) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.unavailable");
        }
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(context.player());
        if (!component.spendMana(manaCost)) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.not_enough_mana");
        }

        effect.run();
        return WitchSkillUseResult.success(Math.max(0, cooldownAfter.getAsInt()), successMessageKey);
    }
}
