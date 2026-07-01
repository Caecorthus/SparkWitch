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
                GrandWitchRules.CEREMONIAL_SWORD_INITIAL_COOLDOWN_TICKS,
                0,
                0,
                context -> GrandWitchRules.isGrandWitch(context.role()),
                GrandWitchActiveSkillService::use
        ));
        registerApprenticeSkill(
                ApprenticeWitchSkillRules.MIGHTY_FORCE_ID,
                0x75EDFA,
                ApprenticeWitchSkillRules.MIGHTY_FORCE_COOLDOWN_TICKS,
                ApprenticeWitchSkillRules.MIGHTY_FORCE_MANA_COST,
                ApprenticeWitchSkillService::useMightyForce
        );
        registerApprenticeSkill(
                ApprenticeWitchSkillRules.SWIFT_STEP_ID,
                0x9CF26F,
                ApprenticeWitchSkillRules.SWIFT_STEP_COOLDOWN_TICKS,
                ApprenticeWitchSkillRules.SWIFT_STEP_MANA_COST,
                ApprenticeWitchSkillService::useSwiftStep
        );
        registerApprenticeSkill(
                ApprenticeWitchSkillRules.MURDER_SENSE_ID,
                ApprenticeWitchSkillRules.MURDER_SENSE_COLOR,
                ApprenticeWitchSkillRules.MURDER_SENSE_COOLDOWN_TICKS,
                ApprenticeWitchSkillRules.MURDER_SENSE_MANA_COST,
                ApprenticeWitchSkillService::useMurderSense
        );
        registerApprenticeSkill(
                ApprenticeWitchSkillRules.HEALING_ID,
                0x6DF2A2,
                ApprenticeWitchSkillRules.HEALING_COOLDOWN_TICKS,
                ApprenticeWitchSkillRules.HEALING_MANA_COST,
                ApprenticeWitchSkillService::useHealing
        );
        registerApprenticeSkill(
                ApprenticeWitchSkillRules.CLAIRVOYANCE_ID,
                ApprenticeWitchSkillRules.CLAIRVOYANCE_SELF_COLOR,
                ApprenticeWitchSkillRules.CLAIRVOYANCE_COOLDOWN_TICKS,
                ApprenticeWitchSkillRules.CLAIRVOYANCE_MANA_COST,
                ApprenticeWitchSkillService::useClairvoyance
        );
        WitchSkillRegistry.register(new WitchSkillDefinition(
                PigGodRules.PIG_CHASE_ID,
                PigGodRules.COLOR,
                1,
                0,
                PigGodRules.COOLDOWN_TICKS,
                0,
                context -> context.role() == dev.caecorthus.sparkwitch.SparkWitchRoles.pigGod(),
                PigGodSkillService::use
        ));
        WitchSkillRegistry.register(new WitchSkillDefinition(
                MurderousWitchDeathRayRules.DEATH_RAY_ID,
                MurderousWitchDeathRayRules.COLOR,
                1,
                MurderousWitchDeathRayRules.INITIAL_COOLDOWN_TICKS,
                MurderousWitchDeathRayRules.COOLDOWN_TICKS,
                MurderousWitchDeathRayRules.MANA_COST,
                context -> MurderousWitchDeathRayRules.canSelect(context.role()),
                MurderousWitchDeathRayService::use
        ));
    }

    static synchronized void resetForTests() {
        registered = false;
    }

    private static void registerApprenticeSkill(
            net.minecraft.util.Identifier id,
            int color,
            int cooldownTicks,
            int manaCost,
            java.util.function.Function<dev.caecorthus.sparkwitch.api.WitchSkillUseContext, dev.caecorthus.sparkwitch.api.WitchSkillUseResult> useHandler
    ) {
        WitchSkillRegistry.register(new WitchSkillDefinition(
                id,
                color,
                1,
                ApprenticeWitchSkillRules.INITIAL_COOLDOWN_TICKS,
                cooldownTicks,
                manaCost,
                context -> context.role() == dev.caecorthus.sparkwitch.SparkWitchRoles.apprenticeWitch(),
                useHandler
        ));
    }
}
