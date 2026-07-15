package dev.caecorthus.sparkwitch.roles.civilian.saint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaintRegistrationSourceTest {
    private static final Path REGISTRY = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/registry/SparkWitchRoleRegistry.java");
    private static final Path FEATURE_SERVICE = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/roles/civilian/saint/SaintFeatureService.java");
    private static final Path KARMA_SERVICE = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/roles/civilian/saint/SaintKarmaService.java");
    private static final Path KARMA_RUNTIME = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/roles/civilian/saint/SaintKarmaRuntime.java");
    private static final Path KILL_PROTECTION_MIXIN = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/mixin/GameFunctionsSaintProtectionMixin.java");
    private static final Path MIXIN_CONFIG = Path.of("src/main/resources/sparkwitch.mixins.json");

    @Test
    void registersTheApprovedAlwaysEligibleCivilianPassives() throws IOException {
        String source = Files.readString(REGISTRY);
        int start = source.indexOf("saint = SparkFactionApi.registerRole");
        int end = source.indexOf("private static void registerNativeWatheRoles", start);
        assertTrue(start >= 0 && end > start);
        String saintRegistration = source.substring(start, end);

        assertTrue(saintRegistration.contains("builder(SAINT_ID, FactionIds.CIVILIAN)"));
        assertTrue(saintRegistration.contains(".color(SaintRules.COLOR)"));
        assertTrue(saintRegistration.contains(".moodType(Role.MoodType.NONE)"));
        assertTrue(saintRegistration.contains(".maxSprintTime(GameConstants.getInTicks(0, 10))"));
        assertTrue(saintRegistration.contains(".canSeeTime(false)"));
        assertTrue(saintRegistration.contains(".nativeWatheFaction(Faction.CIVILIAN)"));
        assertFalse(saintRegistration.contains("minPlayers"));
    }

    @Test
    void protectsSaintBeforeWatheCanShortCircuitKillListeners() throws IOException {
        assertTrue(Files.isRegularFile(KILL_PROTECTION_MIXIN));

        String mixin = Files.readString(KILL_PROTECTION_MIXIN);
        String featureService = Files.readString(FEATURE_SERVICE);
        String mixinConfig = Files.readString(MIXIN_CONFIG);

        assertTrue(mixin.contains("@Mixin(GameFunctions.class)"));
        assertTrue(mixin.contains("method = \"killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;Z"
                + "Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V\""));
        assertTrue(mixin.contains("at = @At(\"HEAD\")"));
        assertTrue(mixin.contains("cancellable = true"));
        assertTrue(mixin.contains("SaintFeatureService.blocksKill"));
        assertTrue(mixin.contains("ci.cancel()"));
        assertFalse(featureService.contains("KillPlayer.BEFORE.register"));
        assertTrue(featureService.contains("KillPlayer.AFTER.register"));
        assertTrue(mixinConfig.contains("\"GameFunctionsSaintProtectionMixin\""));
    }

    @Test
    void clearsKarmaWhenGrandWitchIsAssignedOrObserved() throws IOException {
        String featureService = Files.readString(FEATURE_SERVICE);
        String karmaService = Files.readString(KARMA_SERVICE);
        String karmaRuntime = Files.readString(KARMA_RUNTIME);

        assertTrue(featureService.contains("SaintRules.isKarmaImmune(role)"));
        assertTrue(featureService.contains("SaintKarmaService.clear(player)"));
        assertTrue(karmaService.contains("worldComponent.clearSaintKarma(player.getUuid())"));
        assertTrue(karmaRuntime.contains("SaintKarmaService.clear(player)"));
        assertFalse(karmaRuntime.contains("effectiveKarmaTicks"));
    }
}
