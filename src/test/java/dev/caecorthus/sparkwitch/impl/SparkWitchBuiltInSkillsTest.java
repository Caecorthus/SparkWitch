package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.api.WitchSkillDefinition;
import dev.caecorthus.sparkwitch.api.WitchSkillRegistry;
import dev.caecorthus.sparkwitch.api.WitchSkillSelectionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

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
    void apprenticeSkillDefinitionsCarryInitialCooldownAndManaCost() {
        SparkWitchBuiltInSkills.register();

        for (var id : ApprenticeWitchSkillRules.SKILL_IDS) {
            WitchSkillDefinition skill = WitchSkillRegistry.get(id);

            assertEquals(ApprenticeWitchSkillRules.INITIAL_COOLDOWN_TICKS, skill.initialCooldownTicks());
        }

        assertEquals(ApprenticeWitchSkillRules.MIGHTY_FORCE_MANA_COST,
                WitchSkillRegistry.get(ApprenticeWitchSkillRules.MIGHTY_FORCE_ID).manaCost());
        assertEquals(ApprenticeWitchSkillRules.SWIFT_STEP_MANA_COST,
                WitchSkillRegistry.get(ApprenticeWitchSkillRules.SWIFT_STEP_ID).manaCost());
        assertEquals(ApprenticeWitchSkillRules.MURDER_SENSE_MANA_COST,
                WitchSkillRegistry.get(ApprenticeWitchSkillRules.MURDER_SENSE_ID).manaCost());
        assertEquals(ApprenticeWitchSkillRules.HEALING_MANA_COST,
                WitchSkillRegistry.get(ApprenticeWitchSkillRules.HEALING_ID).manaCost());
        assertEquals(ApprenticeWitchSkillRules.CLAIRVOYANCE_MANA_COST,
                WitchSkillRegistry.get(ApprenticeWitchSkillRules.CLAIRVOYANCE_ID).manaCost());
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
        assertEquals(0, skill.initialCooldownTicks());
        assertEquals(MurderousWitchDeathRayRules.COOLDOWN_TICKS, skill.cooldownTicks());
        assertEquals(MurderousWitchDeathRayRules.MANA_COST, skill.manaCost());
    }
}
