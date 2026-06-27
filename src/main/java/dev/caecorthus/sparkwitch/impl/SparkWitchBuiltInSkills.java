package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.api.WitchSkillDefinition;
import dev.caecorthus.sparkwitch.api.WitchSkillRegistry;

public final class SparkWitchBuiltInSkills {
    private static boolean registered;

    private SparkWitchBuiltInSkills() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        WitchSkillRegistry.register(new WitchSkillDefinition(
                GrandWitchActiveSkillService.CEREMONIAL_SWORD_SKILL_ID,
                0xF2DFF7,
                1,
                0,
                context -> GrandWitchRules.isGrandWitch(context.role()),
                GrandWitchActiveSkillService::use
        ));
    }
}
