package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenActingRole;

/** Narrow primary-skill adapter for Black Raven Perception. */
public final class BlackRavenSkillService {
    private BlackRavenSkillService() {
    }

    public static WitchSkillUseResult use(WitchSkillUseContext context) {
        // Perception is part of the Black Raven kit, which a disguise disables. / 感知属于黑羽鸦能力，伪装期间禁用。
        if (BlackRavenActingRole.isDisguised(context.player())) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.perception.disguised");
        }
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
