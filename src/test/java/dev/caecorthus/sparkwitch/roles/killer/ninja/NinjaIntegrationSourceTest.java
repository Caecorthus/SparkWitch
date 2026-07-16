package dev.caecorthus.sparkwitch.roles.killer.ninja;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NinjaIntegrationSourceTest {
    private static final Path REGISTRY = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/registry/SparkWitchRoleRegistry.java");
    private static final Path FEATURE_SERVICE = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/roles/killer/ninja/NinjaFeatureService.java");
    private static final Path SHOP_SERVICE = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/roles/killer/ninja/NinjaShopService.java");
    private static final Path SKILL_SERVICE = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/roles/killer/ninja/NinjaSkillService.java");

    @Test
    void registersNinjaAsOneNativeWatheSpecialKillerCandidate() throws IOException {
        String source = Files.readString(REGISTRY);
        int start = source.indexOf("ninja = SparkFactionApi.registerRole");
        int end = source.indexOf(".build());", start);
        assertTrue(start >= 0 && end > start);
        String registration = source.substring(start, end + ".build());".length());

        assertTrue(registration.contains("builder(NINJA_ID, FactionIds.KILLER)"));
        assertTrue(registration.contains(".color(NinjaRules.COLOR)"));
        assertTrue(registration.contains(".moodType(Role.MoodType.FAKE)"));
        assertTrue(registration.contains(".maxSprintTime(-1)"));
        assertTrue(registration.contains(".canSeeTime(true)"));
        assertTrue(registration.contains(".nativeWatheFaction(Faction.KILLER)"));
        assertFalse(registration.contains("minPlayers"));
        assertFalse(source.contains("assignNinja"));
    }

    @Test
    void assignmentDoesNotGiveNinjaAStartingLockpick() throws IOException {
        String source = Files.readString(FEATURE_SERVICE);

        assertTrue(source.contains("NinjaRules.isNinja(role)"));
        assertFalse(source.contains("WatheItems.LOCKPICK"));
        assertFalse(source.contains("player.giveItemStack"));
    }

    @Test
    void shopPreservesTheExactWatheBlackoutEntryAndReplacesEverythingElse() throws IOException {
        String source = Files.readString(SHOP_SERVICE);

        assertTrue(source.contains("\"blackout\".equals(entry.id())"));
        assertTrue(source.contains("context.clearEntries()"));
        assertTrue(source.contains("context.addEntry(blackoutEntry)"));
        assertFalse(source.contains("PlayerShopComponent::useBlackout"));
        assertFalse(source.contains("triggerBlackout"));
    }

    @Test
    void shopLimitsTheKunaiToOnePurchase() throws IOException {
        String source = Files.readString(SHOP_SERVICE);
        int start = source.indexOf("\"ninja_knife\"");
        int end = source.indexOf(".build()", start);

        assertTrue(start >= 0 && end > start);
        assertTrue(source.substring(start, end).contains(".stock(1)"));
    }

    @Test
    void parryListensOnlyForPlayerCausedLethalKillAttempts() throws IOException {
        String source = Files.readString(FEATURE_SERVICE);

        assertTrue(source.contains("KillPlayer.BEFORE.register"));
        assertTrue(source.contains("killer == null"));
        assertTrue(source.contains("GameFunctions.isPlayerPlayingAndAlive(victim)"));
        assertTrue(source.contains("victim.getUuid().equals(killer.getUuid())"));
        assertFalse(source.contains("ALLOW_DAMAGE"));
        assertFalse(source.contains("ALLOW_DEATH"));
    }

    @Test
    void confirmedDeathCleansWeaponsAndUsesKillersRawBrightnessOrWatheBlackout() throws IOException {
        String source = Files.readString(FEATURE_SERVICE);

        assertTrue(source.contains("KillPlayer.AFTER.register"));
        assertTrue(source.contains("removeNinjaWeapons(victim)"));
        assertTrue(source.contains("getBaseLightLevel(killer.getBlockPos(), 0)"));
        assertTrue(source.contains("WorldBlackoutComponent.KEY.get"));
        assertTrue(source.contains("SparkWitchItems.ninjaKnife()"));
        assertTrue(source.contains("SparkWitchItems.ninjaShuriken()"));
        assertTrue(source.contains("player.playerScreenHandler.getCraftingInput()"));
    }

    @Test
    void skillDefersTheSharedCooldownUntilParryEnds() throws IOException {
        String source = Files.readString(SKILL_SERVICE);

        assertTrue(source.contains("beginNinjaParryWindow(NinjaRules.PARRY_WINDOW_TICKS)"));
        assertTrue(source.contains("WitchSkillUseResult.successAfterActiveWindow"));
        assertTrue(source.contains("NinjaRules.PARRY_COOLDOWN_TICKS"));
    }
}
