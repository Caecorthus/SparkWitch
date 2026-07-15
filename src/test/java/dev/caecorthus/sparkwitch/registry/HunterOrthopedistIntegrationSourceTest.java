package dev.caecorthus.sparkwitch.registry;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HunterOrthopedistIntegrationSourceTest {
    @Test
    void registryUsesNativeFactionsAndKeepsBothRolesOutOfWitchSkillMembership() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/dev/caecorthus/sparkwitch/registry/SparkWitchRoleRegistry.java"));

        assertTrue(source.contains("FactionRoleDefinition.builder(HUNTER_ID, FactionIds.KILLER)"));
        assertTrue(source.contains(".nativeWatheFaction(Faction.KILLER)"));
        assertTrue(source.contains("FactionRoleDefinition.builder(ORTHOPEDIST_ID, FactionIds.CIVILIAN)"));
        assertTrue(source.contains(".nativeWatheFaction(Faction.CIVILIAN)"));

        int membershipStart = source.indexOf("private static boolean isRegisteredSparkWitchRole");
        String membership = source.substring(membershipStart);
        assertFalse(membership.contains("role == hunter"));
        assertFalse(membership.contains("role == orthopedist"));
    }

    @Test
    void pairingRunsBeforeWitchAndOrdinaryCivilianAssignment() throws IOException {
        String mixin = Files.readString(Path.of(
                "src/main/java/dev/caecorthus/sparkwitch/mixin/MurderGameModeMixin.java"));

        int pairing = mixin.indexOf("HunterOrthopedistPairingService.ensurePairBeforeCivilians");
        int witches = mixin.indexOf("WitchRoleAssignmentService.assignAfterNeutralsBeforeCivilians");
        assertTrue(pairing >= 0);
        assertTrue(witches > pairing);
        assertTrue(mixin.contains("ScoreboardRoleSelectorComponent;assignCivilians"));
    }
}
