package dev.caecorthus.sparkwitch.roles.witch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitchFactionFeatureServiceTest {
    private static final Path FACADE_SOURCE =
            Path.of("src/main/java/dev/caecorthus/sparkwitch/roles/witch/WitchFactionFeatureService.java");
    private static final Path PROTECTION_SOURCE =
            Path.of("src/main/java/dev/caecorthus/sparkwitch/roles/witch/WitchFactionProtectionPolicy.java");

    @Test
    void registersSparkFactionAndShopPoliciesInApprovedOrder() throws IOException {
        String facadeSource = Files.readString(FACADE_SOURCE);
        assertInOrder(
                facadeSource,
                "SparkFactionApi.registerEconomyPolicy(WitchFactionEconomyPolicy::economyDecision);",
                "SparkFactionApi.registerInstinctPolicy(WitchInstinctPolicy::instinctHighlight);",
                "WitchFactionProtectionPolicy.register();",
                "GrandWitchShopService.register();",
                "AccompliceShopService.register();"
        );
        assertInOrder(
                facadeSource,
                "WitchFactionEconomyPolicy.assignStartingLoadout(player, role);",
                "if (!WitchFactionRules.isGrandWitch(role)) {",
                "GrandWitchActiveSkillService.clearCeremonialSword(player, false);"
        );

        String protectionSource = Files.readString(PROTECTION_SOURCE);
        assertInOrder(
                protectionSource,
                "BlackoutEffect.BEFORE.register(WitchFactionProtectionPolicy::beforeBlackoutEffect);",
                "KillPlayer.BEFORE.register(WitchFactionProtectionPolicy::beforeKillPlayer);"
        );
    }

    private static void assertInOrder(String source, String... needles) {
        int previous = -1;
        for (String needle : needles) {
            int current = source.indexOf(needle);
            assertTrue(current > previous, () -> "Expected ordered source entry: " + needle);
            previous = current;
        }
    }
}
