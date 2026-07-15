package dev.caecorthus.sparkwitch.roles.killer.hunter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HunterFeatureServiceSourceTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/roles/killer/hunter/HunterFeatureService.java");

    @Test
    void wiresLineOfSightIntoDismantlingAndDropsAReclaimedTrapWhenInventoryIsFull() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains(
                "HunterRules.canDismantle(role.identifier(), player.canSee(trap))"));
        assertTrue(source.contains("ItemStack returnedTrap ="));
        assertTrue(source.contains("if (!player.giveItemStack(returnedTrap))"));
        assertTrue(source.contains("player.dropItem(returnedTrap, false)"));
    }

    @Test
    void delegatesOptionalNoellesCooldownIdsToThePureRuleSet() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("HunterRules.isExtraDismantleCooldownItem(itemId)"));
    }
}
