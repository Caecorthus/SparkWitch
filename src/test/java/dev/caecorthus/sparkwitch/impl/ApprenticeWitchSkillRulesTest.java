package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprenticeWitchSkillRulesTest {
    @Test
    void apprenticeSkillPoolContainsExactlyTheFivePlannedSkills() {
        assertEquals(List.of(
                SparkWitch.id("mighty_force"),
                SparkWitch.id("swift_step"),
                SparkWitch.id("murder_sense"),
                SparkWitch.id("healing"),
                SparkWitch.id("clairvoyance")
        ), ApprenticeWitchSkillRules.SKILL_IDS);
    }

    @Test
    void apprenticeSkillTimingsAndManaCostsMatchDesign() {
        assertEquals(GameConstants.getInTicks(1, 0), ApprenticeWitchSkillRules.INITIAL_COOLDOWN_TICKS);

        assertEquals(100, ApprenticeWitchSkillRules.MIGHTY_FORCE_MANA_COST);
        assertEquals(GameConstants.getInTicks(0, 10), ApprenticeWitchSkillRules.MIGHTY_FORCE_WINDOW_TICKS);
        assertEquals(GameConstants.getInTicks(5, 0), ApprenticeWitchSkillRules.MIGHTY_FORCE_COOLDOWN_TICKS);

        assertEquals(50, ApprenticeWitchSkillRules.SWIFT_STEP_MANA_COST);
        assertEquals(GameConstants.getInTicks(0, 5), ApprenticeWitchSkillRules.SWIFT_STEP_DURATION_TICKS);
        assertEquals(GameConstants.getInTicks(2, 0), ApprenticeWitchSkillRules.SWIFT_STEP_COOLDOWN_TICKS);

        assertEquals(80, ApprenticeWitchSkillRules.MURDER_SENSE_MANA_COST);
        assertEquals(GameConstants.getInTicks(0, 15), ApprenticeWitchSkillRules.MURDER_SENSE_DURATION_TICKS);
        assertEquals(GameConstants.getInTicks(1, 0), ApprenticeWitchSkillRules.MURDER_SENSE_COOLDOWN_TICKS);
        assertEquals(20.0, runtimeDoubleConstant("MURDER_SENSE_RANGE_BLOCKS"));

        assertEquals(60, ApprenticeWitchSkillRules.HEALING_MANA_COST);
        assertEquals(GameConstants.getInTicks(0, 20), ApprenticeWitchSkillRules.HEALING_DURATION_TICKS);
        assertEquals(GameConstants.getInTicks(2, 0), ApprenticeWitchSkillRules.HEALING_COOLDOWN_TICKS);

        assertEquals(100, ApprenticeWitchSkillRules.CLAIRVOYANCE_MANA_COST);
        assertEquals(GameConstants.getInTicks(0, 30), ApprenticeWitchSkillRules.CLAIRVOYANCE_SELF_TICKS);
        assertEquals(GameConstants.getInTicks(0, 10), ApprenticeWitchSkillRules.CLAIRVOYANCE_OTHERS_TICKS);
        assertEquals(GameConstants.getInTicks(3, 0), ApprenticeWitchSkillRules.CLAIRVOYANCE_COOLDOWN_TICKS);
    }

    @Test
    void dangerousItemWhitelistContainsOnlyNamedItems() {
        Set<Identifier> dangerousItems = ApprenticeWitchSkillRules.DANGEROUS_ITEM_IDS;

        assertTrue(dangerousItems.contains(Identifier.of("wathe", "revolver")));
        assertTrue(dangerousItems.contains(Identifier.of("wathe", "derringer")));
        assertTrue(dangerousItems.contains(Identifier.of("noellesroles", "demon_hunter_pistol")));
        assertTrue(dangerousItems.contains(Identifier.of("wathe", "knife")));
        assertTrue(dangerousItems.contains(Identifier.of("wathe", "bat")));
        assertTrue(dangerousItems.contains(Identifier.of("wathe", "grenade")));
        assertTrue(dangerousItems.contains(Identifier.of("wathe", "poison_vial")));
        assertTrue(dangerousItems.contains(Identifier.of("wathe", "scorpion")));
        assertTrue(dangerousItems.contains(Identifier.of("noellesroles", "poison_needle")));
        assertTrue(dangerousItems.contains(Identifier.of("noellesroles", "poison_gas_bomb")));
        assertTrue(dangerousItems.contains(Identifier.of("noellesroles", "throwing_axe")));
        assertTrue(dangerousItems.contains(SparkWitch.id("ceremonial_sword")));
        assertTrue(dangerousItems.contains(SparkWitch.id("fire_poker")));
        assertEquals(13, dangerousItems.size());
    }

    private static double runtimeDoubleConstant(String fieldName) {
        try {
            return ApprenticeWitchSkillRules.class.getField(fieldName).getDouble(null);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Missing Apprentice Witch skill tuning field: " + fieldName, exception);
        }
    }
}
