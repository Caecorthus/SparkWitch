package dev.caecorthus.sparkwitch.roles.civilian.saint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SaintEconomyIntegrationSourceTest {
    private static final Path ABILITY = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/roles/civilian/saint/SaintAbilityService.java");
    private static final Path FEATURE = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/roles/civilian/saint/SaintFeatureService.java");
    private static final Path EVENTS = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/impl/SparkWitchEvents.java");

    @Test
    void serverChecksAndDeductsHellfireCostBeforeActivating() throws IOException {
        String source = Files.readString(ABILITY).replace("\r\n", "\n").replace('\r', '\n');
        int balanceCheck = source.indexOf("SaintRules.hasHellfireCoinRequirement(shop.getBalance())");
        int deduction = source.indexOf(
                "shop.setBalance(shop.getBalance() - SaintRules.HELLFIRE_REQUIRED_COINS)");
        int activation = source.indexOf("state.activateHellfire()");

        assertTrue(source.contains("PlayerShopComponent.KEY.get(player)"));
        assertTrue(balanceCheck >= 0);
        assertTrue(deduction > balanceCheck);
        assertTrue(activation > deduction);
        assertTrue(source.contains("message.sparkwitch.saint.hellfire.not_enough_money"));
    }

    @Test
    void economyUsesSaintRoleAssignmentVisibilityAndTaskLifecycle() throws IOException {
        String feature = Files.readString(FEATURE);
        String events = Files.readString(EVENTS);

        assertTrue(feature.contains("SaintEconomyService.register()"));
        assertTrue(feature.contains("SaintEconomyService.assignForRole(player, role)"));
        assertTrue(events.contains("SaintEconomyService.onTaskComplete(player)"));
    }
}
