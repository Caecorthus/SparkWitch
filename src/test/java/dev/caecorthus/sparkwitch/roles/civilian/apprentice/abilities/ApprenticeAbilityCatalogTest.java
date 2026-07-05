package dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.Clairvoyance.ClairvoyanceAbility;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.Healing.HealingAbility;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MightyForce.MightyForceAbility;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MurderSense.MurderSenseAbility;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.SwiftStep.SwiftStepAbility;
import dev.doctor4t.wathe.game.GameConstants;
import java.util.List;
import java.util.Set;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprenticeAbilityCatalogTest {
    @Test
    void apprenticeAbilityPoolContainsExactlyTheFivePlannedAbilities() {
        assertEquals(List.of(
                SparkWitch.id("mighty_force"),
                SparkWitch.id("swift_step"),
                SparkWitch.id("murder_sense"),
                SparkWitch.id("healing"),
                SparkWitch.id("clairvoyance")
        ), ApprenticeAbilityCatalog.ABILITY_IDS);
    }

    @Test
    void apprenticeAbilityTimingsAndManaCostsMatchDesign() {
        assertEquals(GameConstants.getInTicks(1, 0), ApprenticeAbilityCatalog.INITIAL_COOLDOWN_TICKS);

        assertEquals(80, MightyForceAbility.MANA_COST);
        assertEquals(GameConstants.getInTicks(0, 10), MightyForceAbility.WINDOW_TICKS);
        assertEquals(GameConstants.getInTicks(1, 0), MightyForceAbility.COOLDOWN_TICKS);

        assertEquals(30, SwiftStepAbility.MANA_COST);
        assertEquals(GameConstants.getInTicks(0, 5), SwiftStepAbility.DURATION_TICKS);
        assertEquals(GameConstants.getInTicks(0, 30), SwiftStepAbility.COOLDOWN_TICKS);

        assertEquals(60, MurderSenseAbility.MANA_COST);
        assertEquals(GameConstants.getInTicks(0, 15), MurderSenseAbility.DURATION_TICKS);
        assertEquals(GameConstants.getInTicks(1, 0), MurderSenseAbility.COOLDOWN_TICKS);
        assertEquals(20.0, runtimeDoubleConstant("RANGE_BLOCKS"));

        assertEquals(40, HealingAbility.MANA_COST);
        assertEquals(GameConstants.getInTicks(0, 20), HealingAbility.DURATION_TICKS);
        assertEquals(GameConstants.getInTicks(2, 0), HealingAbility.COOLDOWN_TICKS);

        assertEquals(80, ClairvoyanceAbility.MANA_COST);
        assertEquals(GameConstants.getInTicks(0, 30), ClairvoyanceAbility.SELF_TICKS);
        assertEquals(GameConstants.getInTicks(0, 10), ClairvoyanceAbility.OTHERS_TICKS);
        assertEquals(GameConstants.getInTicks(1, 0), ClairvoyanceAbility.COOLDOWN_TICKS);
    }

    @Test
    void dangerousItemWhitelistContainsOnlyNamedItems() {
        Set<Identifier> dangerousItems = MurderSenseAbility.DANGEROUS_ITEM_IDS;

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
            return MurderSenseAbility.class.getField(fieldName).getDouble(null);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Missing Apprentice Witch ability tuning field: " + fieldName, exception);
        }
    }
}
