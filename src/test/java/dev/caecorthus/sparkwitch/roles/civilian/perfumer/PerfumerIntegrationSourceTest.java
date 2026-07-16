package dev.caecorthus.sparkwitch.roles.civilian.perfumer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfumerIntegrationSourceTest {
    private static final Path MAIN = Path.of("src/main/java/dev/caecorthus/sparkwitch");

    @Test
    void registersTheCivilianRoleAndItsOwnerOnlyComponent() throws IOException {
        String registry = read("registry/SparkWitchRoleRegistry.java");
        String components = read("component/SparkWitchComponents.java");
        String component = read("component/PerfumerPlayerComponent.java");
        String metadata = Files.readString(Path.of("src/main/resources/fabric.mod.json"));

        assertTrue(registry.contains("PERFUMER_ID = SparkWitch.id(\"perfumer\")"));
        assertTrue(registry.contains("FactionRoleDefinition.builder(PERFUMER_ID, FactionIds.CIVILIAN)"));
        assertTrue(registry.contains(".nativeWatheFaction(Faction.CIVILIAN)"));
        assertTrue(components.contains("PerfumerPlayerComponent.KEY"));
        assertTrue(component.contains("recipient == this.player"));
        assertTrue(metadata.contains("sparkwitch:perfumer_player"));
    }

    @Test
    void registersUnlimitedPerfumerShopEntriesAtApprovedPrices() throws IOException {
        String shop = read("roles/civilian/perfumer/PerfumerShopService.java");
        String normalized = shop.replaceAll("\\s+", " ");

        assertTrue(normalized.contains("\"perfume_essence\", SparkWitchItems.perfumeEssence().getDefaultStack(), PerfumerRules.PERFUME_ESSENCE_PRICE"));
        assertTrue(normalized.contains("\"cologne\", SparkWitchItems.cologne().getDefaultStack(), PerfumerRules.COLOGNE_PRICE"));
        assertFalse(shop.contains(".stock("));
    }

    @Test
    void usesServerAuthorityForKillPromotionHealingAndRoundCleanup() throws IOException {
        String runtime = read("roles/civilian/perfumer/PerfumerRuntime.java");
        String hiddenBodiesBridge = read("compat/NoellesHiddenBodiesBridge.java");
        String events = read("impl/SparkWitchEvents.java");

        assertTrue(runtime.contains("KillPlayer.AFTER.register"));
        assertTrue(runtime.contains("NoellesHiddenBodiesBridge.isHidden"));
        assertFalse(runtime.contains("HiddenBodiesWorldComponent"));
        assertTrue(hiddenBodiesBridge.contains("HiddenBodiesWorldComponent.KEY"));
        assertTrue(runtime.contains("PlayerMoodComponent.KEY"));
        assertTrue(events.contains("PerfumerRuntime.register()"));
        assertTrue(events.contains("PerfumerPlayerComponent.KEY.get(victim).stopCologne()"));
        assertFalse(events.contains("PerfumerPlayerComponent.KEY.get(victim).clear()"));
        assertTrue(events.contains("PerfumerPlayerComponent.KEY.get(player).clear()"));
    }

    @Test
    void registersPassiveOwnerPrivatePlayerAndCorpseHighlights() throws IOException {
        String feature = read("roles/civilian/perfumer/PerfumerFeatureService.java");

        assertTrue(feature.contains("SparkFactionApi.registerInstinctPolicy"));
        assertTrue(feature.contains("target instanceof PlayerEntity"));
        assertTrue(feature.contains("target instanceof PlayerBodyEntity"));
        assertTrue(feature.contains(".isMarked("));
        assertTrue(feature.contains("PerfumerRules.ROLE_COLOR"));
        assertTrue(feature.contains("PerfumerRules.isWithinVisibleOutlineRange"));
        assertTrue(feature.contains("viewer.canSee(targetPlayer)"));
        assertTrue(feature.contains("PerfumerRules.BLOODY_OUTLINE_COLOR"));
        assertTrue(feature.contains("PerfumerRules.CORPSE_OUTLINE_COLOR"));
        assertTrue(feature.contains("NoellesHiddenBodiesBridge.isHidden"));
        assertFalse(feature.contains("HiddenBodiesWorldComponent"));
        assertTrue(feature.contains("InstinctResult.show"));
    }

    @Test
    void registersBothItemsThroughFactionApiNoellesCompat() throws IOException {
        String items = read("SparkWitchItems.java");
        String initializer = read("SparkWitch.java");

        assertTrue(items.contains("PERFUME_ESSENCE_ID = SparkWitch.id(\"perfume_essence\")"));
        assertTrue(items.contains("COLOGNE_ID = SparkWitch.id(\"cologne\")"));
        assertTrue(initializer.contains("NoellesHiddenEquipment.register(SparkWitchItems.perfumeEssence())"));
        assertTrue(initializer.contains("NoellesHiddenEquipment.register(SparkWitchItems.cologne())"));
        assertFalse(initializer.contains("HiddenEquipmentHelper"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(MAIN.resolve(relativePath));
    }
}
