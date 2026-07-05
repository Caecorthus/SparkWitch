package dev.caecorthus.sparkwitch.skill;

import dev.caecorthus.sparkwitch.api.WitchSkillDefinition;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;

final class WitchSkillCooldownPolicy {
    private WitchSkillCooldownPolicy() {
    }

    static Decision decide(WitchSkillDefinition skill, WitchSkillUseResult result) {
        return new Decision(
                Math.max(skill.cooldownTicks(), result.cooldownTicks()),
                result.deferCooldownUntilActiveWindowEnds()
        );
    }

    static void apply(WitchPlayerComponent component, Decision decision) {
        if (decision.deferUntilActiveWindowEnds()) {
            component.deferCooldownUntilActiveWindowEnds(decision.cooldownTicks());
        } else {
            component.setCooldownTicks(decision.cooldownTicks());
        }
    }

    record Decision(int cooldownTicks, boolean deferUntilActiveWindowEnds) {
        Decision {
            cooldownTicks = Math.max(0, cooldownTicks);
        }
    }
}
