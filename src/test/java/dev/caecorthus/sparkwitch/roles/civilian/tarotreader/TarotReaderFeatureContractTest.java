package dev.caecorthus.sparkwitch.roles.civilian.tarotreader;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TarotReaderFeatureContractTest {
    private static final Path ROLE_REGISTRY = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/registry/SparkWitchRoleRegistry.java"
    );
    private static final Path ROLE_FACADE = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/SparkWitchRoles.java"
    );
    private static final Path PACKET_REGISTRY = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/net/SparkWitchPackets.java"
    );
    private static final Path SHOP_SERVICE = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/roles/civilian/tarotreader/TarotReaderShopService.java"
    );
    private static final Path FEATURE_SERVICE = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/roles/civilian/tarotreader/TarotReaderFeatureService.java"
    );

    @Test
    void roleUsesTheApprovedStableContractAndGuessOrder() throws Exception {
        String registry = Files.readString(ROLE_REGISTRY);
        String facade = Files.readString(ROLE_FACADE);

        assertTrue(registry.contains("TAROT_READER_ID = SparkWitch.id(\"tarot_reader\")"));
        assertTrue(registry.contains("FactionRoleDefinition.builder(TAROT_READER_ID, FactionIds.CIVILIAN)"));
        assertTrue(registry.contains(".color(TarotReaderRules.COLOR)"));
        assertTrue(registry.contains(".moodType(Role.MoodType.REAL)"));
        assertTrue(registry.contains(".maxSprintTime(GameConstants.getInTicks(0, 10))"));
        assertTrue(registry.contains(".canSeeTime(false)"));
        assertTrue(registry.contains(".appearanceCondition(RoleAppearanceCondition.ALWAYS)"));
        assertTrue(registry.contains(".nativeWatheFaction(Faction.CIVILIAN)"));

        int guessOrder = registry.indexOf("private static List<Role> assassinGuessRolesInOrder()");
        int listStart = registry.indexOf("return List.of(", guessOrder);
        int listEnd = registry.indexOf(");", listStart);
        String guessRoles = registry.substring(listStart, listEnd);
        int pigGod = guessRoles.indexOf("pigGod");
        int tarotReader = guessRoles.indexOf("tarotReader");
        int murderousWitch = guessRoles.indexOf("murderousWitch");
        assertTrue(pigGod >= 0 && pigGod < tarotReader && tarotReader < murderousWitch,
                "Tarot Reader must be inserted immediately after Pig God without moving later roles");
        assertTrue(guessRoles.replaceAll("\\s+", "").contains("pigGod,tarotReader,"),
                "Tarot Reader must remain immediately after Pig God");

        assertTrue(facade.contains("TAROT_READER_ID = SparkWitchRoleRegistry.TAROT_READER_ID"));
        assertTrue(facade.contains("public static Role tarotReader()"));
    }

    @Test
    void packetRegistryOwnsAllThreeApprovedTarotPayloads() throws Exception {
        String packets = Files.readString(PACKET_REGISTRY);

        assertTrue(packets.contains("TarotDivinationSnapshotS2CPacket.ID"));
        assertTrue(packets.contains("OpenTarotDivinationSelectorS2CPacket.ID"));
        assertTrue(packets.contains("SubmitTarotDivinationSelectionC2SPacket.ID"));
        assertTrue(packets.contains("TarotReaderDivinationService.submit"));
    }

    @Test
    void exclusiveShopContainsOnlyTheApprovedPrices() throws Exception {
        assertTrue(Files.exists(SHOP_SERVICE));
        String shop = Files.readString(SHOP_SERVICE);

        assertTrue(shop.contains("context.clearEntries()"));
        assertTrue(shop.contains("TarotReaderRules.REGULAR_PRICE"));
        assertTrue(shop.contains("TarotReaderRules.IDENTITY_PRICE"));
        assertTrue(shop.contains("TarotReaderRules.SURVIVAL_PRICE"));
        assertTrue(shop.contains("TarotReaderDivinationService::purchaseRegular"));
    }

    @Test
    void roundLifecycleClearsHistoryBeforeRoleAssignmentsAreRecorded() throws Exception {
        String feature = Files.readString(FEATURE_SERVICE);

        assertTrue(feature.contains("GameEvents.ON_GAME_START.register"));
        assertTrue(feature.contains("TarotReaderRoundRoleHistory.clear()"));
        assertTrue(feature.contains("RoleAssigned.EVENT.register"));
        assertTrue(feature.contains("TarotReaderRoundRoleHistory.record(role)"));
    }
}
