package dev.caecorthus.sparkwitch.api;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.impl.WitchSkillSelector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitchSkillRegistryTest {
    @AfterEach
    void clearRegistry() {
        WitchSkillRegistry.clearForTests();
    }

    @Test
    void rejectsDuplicateSkillIds() {
        WitchSkillDefinition definition = testSkill("hex", 1);

        WitchSkillRegistry.register(definition);

        assertThrows(IllegalArgumentException.class, () -> WitchSkillRegistry.register(definition));
    }

    @Test
    void selectorSkipsEmptyAndZeroWeightPools() {
        WitchSkillDefinition zeroWeight = testSkill("zero", 0);

        Optional<WitchSkillDefinition> selected = WitchSkillSelector.selectFrom(
                List.of(zeroWeight),
                new WitchSkillSelectionContext(null, null, null, null),
                new Random(1)
        );

        assertTrue(selected.isEmpty());
    }

    @Test
    void selectorReturnsAtMostOneSkill() {
        WitchSkillDefinition first = testSkill("first", 1);
        WitchSkillDefinition second = testSkill("second", 1);

        Optional<WitchSkillDefinition> selected = WitchSkillSelector.selectFrom(
                List.of(first, second),
                new WitchSkillSelectionContext(null, null, null, null),
                new Random(2)
        );

        assertTrue(selected.isPresent());
        assertEquals(1, selected.stream().count());
    }

    @Test
    void skillDefinitionsCanSeparateInitialAndUseCooldowns() {
        WitchSkillDefinition legacy = testSkill("legacy", 1);
        WitchSkillDefinition apprentice = new WitchSkillDefinition(
                SparkWitch.id("apprentice"),
                0x75EDFA,
                1,
                1200,
                2400,
                50,
                context -> true,
                null
        );

        assertEquals(0, legacy.initialCooldownTicks());
        assertEquals(20, legacy.cooldownTicks());
        assertEquals(0, legacy.manaCost());
        assertEquals(1200, apprentice.initialCooldownTicks());
        assertEquals(2400, apprentice.cooldownTicks());
        assertEquals(50, apprentice.manaCost());
    }

    private static WitchSkillDefinition testSkill(String path, int weight) {
        return new WitchSkillDefinition(SparkWitch.id(path), 0xFFFFFF, weight, 20, context -> true, null);
    }
}
