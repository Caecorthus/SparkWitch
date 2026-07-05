package dev.caecorthus.sparkwitch.skill;

import dev.caecorthus.sparkwitch.api.WitchSkillDefinition;
import dev.caecorthus.sparkwitch.api.WitchSkillRegistry;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.ApprenticeAbilityCatalog;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.Clairvoyance.ClairvoyanceAbility;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.Healing.HealingAbility;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MightyForce.MightyForceAbility;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MurderSense.MurderSenseAbility;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.SwiftStep.SwiftStepAbility;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchActiveSkillService;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchDeathRay.MurderousWitchDeathRayRules;
import dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchDeathRay.MurderousWitchDeathRayService;
import dev.caecorthus.sparkwitch.roles.civilian.piggod.PigGodRules;
import dev.caecorthus.sparkwitch.roles.civilian.piggod.PigGodSkillService;

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
                WitchFactionRules.CEREMONIAL_SWORD_INITIAL_COOLDOWN_TICKS,
                0,
                WitchFactionRules.CEREMONIAL_SWORD_MANA_COST,
                context -> WitchFactionRules.isGrandWitch(context.role()),
                GrandWitchActiveSkillService::use
        ));
        registerApprenticeAbility(
                MightyForceAbility.ID,
                MightyForceAbility.COLOR,
                MightyForceAbility.COOLDOWN_TICKS,
                MightyForceAbility.MANA_COST,
                MightyForceAbility::use
        );
        registerApprenticeAbility(
                SwiftStepAbility.ID,
                SwiftStepAbility.COLOR,
                SwiftStepAbility.COOLDOWN_TICKS,
                SwiftStepAbility.MANA_COST,
                SwiftStepAbility::use
        );
        registerApprenticeAbility(
                MurderSenseAbility.ID,
                MurderSenseAbility.COLOR,
                MurderSenseAbility.COOLDOWN_TICKS,
                MurderSenseAbility.MANA_COST,
                MurderSenseAbility::use
        );
        registerApprenticeAbility(
                HealingAbility.ID,
                HealingAbility.COLOR,
                HealingAbility.COOLDOWN_TICKS,
                HealingAbility.MANA_COST,
                HealingAbility::use
        );
        registerApprenticeAbility(
                ClairvoyanceAbility.ID,
                ClairvoyanceAbility.SELF_COLOR,
                ClairvoyanceAbility.COOLDOWN_TICKS,
                ClairvoyanceAbility.MANA_COST,
                ClairvoyanceAbility::use
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

    private static void registerApprenticeAbility(
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
                ApprenticeAbilityCatalog.INITIAL_COOLDOWN_TICKS,
                cooldownTicks,
                manaCost,
                context -> context.role() == dev.caecorthus.sparkwitch.SparkWitchRoles.apprenticeWitch(),
                useHandler
        ));
    }
}
