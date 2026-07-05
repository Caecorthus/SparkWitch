package dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;

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
}
