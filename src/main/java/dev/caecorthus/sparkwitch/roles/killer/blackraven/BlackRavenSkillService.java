package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;

/** Narrow primary-skill adapter for Black Raven Perception. */
public final class BlackRavenSkillService {
    private BlackRavenSkillService() {
    }

    public static WitchSkillUseResult use(WitchSkillUseContext context) {
        if (!BlackRavenRules.isBlackRaven(context.role())
                || !BlackRavenPerceptionService.activate(context.player())) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.unavailable");
        }
        return WitchSkillUseResult.successAfterActiveWindow(
                BlackRavenRules.PERCEPTION_COOLDOWN_TICKS,
                "message.sparkwitch.skill.perception.activated"
        );
    }
}
