package dev.caecorthus.sparkwitch.skill;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.api.WitchSkillDefinition;
import dev.caecorthus.sparkwitch.api.WitchSkillRegistry;
import dev.caecorthus.sparkwitch.api.WitchSkillSelectionContext;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.ApprenticeAbilityCatalog;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.Clairvoyance.ClairvoyanceAbility;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.Healing.HealingAbility;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MightyForce.MightyForceAbility;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MurderSense.MurderSenseAbility;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.SwiftStep.SwiftStepAbility;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchActiveSkillService;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchDeathRay.MurderousWitchDeathRayRules;
import dev.caecorthus.sparkwitch.roles.civilian.piggod.PigGodRules;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;



class SparkWitchBuiltInSkillsTest {
    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @BeforeEach
    void resetRegistry() {
        WitchSkillRegistry.clearForTests();
        SparkWitchBuiltInSkills.resetForTests();
    }

    @AfterEach
    void clearRegistry() {
        WitchSkillRegistry.clearForTests();
        SparkWitchBuiltInSkills.resetForTests();
    }

    @Test
    void apprenticeWitchReceivesOnlyApprenticeSkillPool() {
        SparkWitchBuiltInSkills.register();

        Set<String> selectedPaths = WitchSkillRegistry.values().stream()
                .filter(skill -> skill.canSelect(new WitchSkillSelectionContext(null, null, null, SparkWitchRoles.apprenticeWitch())))
                .map(WitchSkillDefinition::id)
                .map(id -> id.getPath())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        assertEquals(Set.of("mighty_force", "swift_step", "murder_sense", "healing", "clairvoyance"), selectedPaths);
    }

    @Test
    void grandWitchStillReceivesOnlyCeremonialSword() {
        SparkWitchBuiltInSkills.register();

        Set<String> selectedPaths = WitchSkillRegistry.values().stream()
                .filter(skill -> skill.canSelect(new WitchSkillSelectionContext(null, null, null, SparkWitchRoles.grandWitch())))
                .map(WitchSkillDefinition::id)
                .map(id -> id.getPath())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        assertEquals(Set.of("ceremonial_sword"), selectedPaths);
    }

    @Test
    void grandWitchCeremonialSwordStartsWithoutOpeningCooldownAndShowsManaCost() {
        SparkWitchBuiltInSkills.register();

        WitchSkillDefinition skill = WitchSkillRegistry.get(GrandWitchActiveSkillService.CEREMONIAL_SWORD_SKILL_ID);

        assertEquals(0, skill.initialCooldownTicks());
        assertEquals(0, skill.cooldownTicks());
        assertEquals(WitchFactionRules.CEREMONIAL_SWORD_MANA_COST, skill.manaCost());
    }

    @Test
    void pigGodReceivesOnlyPigChase() {
        SparkWitchBuiltInSkills.register();

        Set<String> selectedPaths = WitchSkillRegistry.values().stream()
                .filter(skill -> skill.canSelect(new WitchSkillSelectionContext(null, null, null, SparkWitchRoles.pigGod())))
                .map(WitchSkillDefinition::id)
                .map(id -> id.getPath())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        assertEquals(Set.of("pig_chase"), selectedPaths);
    }

    @Test
    void murderousWitchReceivesOnlyDeathRay() {
        SparkWitchBuiltInSkills.register();

        Set<String> selectedPaths = WitchSkillRegistry.values().stream()
                .filter(skill -> skill.canSelect(new WitchSkillSelectionContext(null, null, null, SparkWitchRoles.murderousWitch())))
                .map(WitchSkillDefinition::id)
                .map(id -> id.getPath())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        assertEquals(Set.of("death_ray"), selectedPaths);
    }

    @Test
    void apprenticeAbilityDefinitionsCarryInitialCooldownCooldownAndManaCost() {
        SparkWitchBuiltInSkills.register();

        for (var id : ApprenticeAbilityCatalog.ABILITY_IDS) {
            WitchSkillDefinition skill = WitchSkillRegistry.get(id);

            assertEquals(ApprenticeAbilityCatalog.INITIAL_COOLDOWN_TICKS, skill.initialCooldownTicks());
        }

        assertEquals(MightyForceAbility.MANA_COST,
                WitchSkillRegistry.get(MightyForceAbility.ID).manaCost());
        assertEquals(MightyForceAbility.COOLDOWN_TICKS,
                WitchSkillRegistry.get(MightyForceAbility.ID).cooldownTicks());
        assertEquals(SwiftStepAbility.MANA_COST,
                WitchSkillRegistry.get(SwiftStepAbility.ID).manaCost());
        assertEquals(SwiftStepAbility.COOLDOWN_TICKS,
                WitchSkillRegistry.get(SwiftStepAbility.ID).cooldownTicks());
        assertEquals(MurderSenseAbility.MANA_COST,
                WitchSkillRegistry.get(MurderSenseAbility.ID).manaCost());
        assertEquals(MurderSenseAbility.COOLDOWN_TICKS,
                WitchSkillRegistry.get(MurderSenseAbility.ID).cooldownTicks());
        assertEquals(HealingAbility.MANA_COST,
                WitchSkillRegistry.get(HealingAbility.ID).manaCost());
        assertEquals(HealingAbility.COOLDOWN_TICKS,
                WitchSkillRegistry.get(HealingAbility.ID).cooldownTicks());
        assertEquals(ClairvoyanceAbility.MANA_COST,
                WitchSkillRegistry.get(ClairvoyanceAbility.ID).manaCost());
        assertEquals(ClairvoyanceAbility.COOLDOWN_TICKS,
                WitchSkillRegistry.get(ClairvoyanceAbility.ID).cooldownTicks());
    }

    @Test
    void pigChaseDefinitionCarriesCooldownCoinCostInRulesAndNoManaCost() {
        SparkWitchBuiltInSkills.register();

        WitchSkillDefinition skill = WitchSkillRegistry.get(PigGodRules.PIG_CHASE_ID);

        assertEquals(PigGodRules.COLOR, skill.color());
        assertEquals(0, skill.initialCooldownTicks());
        assertEquals(PigGodRules.COOLDOWN_TICKS, skill.cooldownTicks());
        assertEquals(0, skill.manaCost());
        assertEquals(150, PigGodRules.COIN_COST);
    }

    @Test
    void deathRayDefinitionCarriesCooldownAndManaCost() {
        SparkWitchBuiltInSkills.register();

        WitchSkillDefinition skill = WitchSkillRegistry.get(MurderousWitchDeathRayRules.DEATH_RAY_ID);

        assertEquals(MurderousWitchDeathRayRules.COLOR, skill.color());
        assertEquals(MurderousWitchDeathRayRules.INITIAL_COOLDOWN_TICKS, skill.initialCooldownTicks());
        assertEquals(MurderousWitchDeathRayRules.COOLDOWN_TICKS, skill.cooldownTicks());
        assertEquals(MurderousWitchDeathRayRules.MANA_COST, skill.manaCost());
    }
}
