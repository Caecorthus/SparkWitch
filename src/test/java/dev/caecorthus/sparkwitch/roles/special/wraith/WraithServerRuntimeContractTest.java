package dev.caecorthus.sparkwitch.roles.special.wraith;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithServerRuntimeContractTest {
    @Test
    void persistentEffectsAndPromotionRemoveOnlyRestrictedEffects() throws Exception {
        String presence = source("roles/special/wraith/runtime/WraithPresence.java");
        String lifecycle = source("roles/special/wraith/runtime/WraithLifecycle.java");
        String promotion = source("roles/special/wraith/progression/WraithPromotionQueue.java");
        String ownedMixin = source("mixin/WraithOwnedEffectMixin.java");
        assertTrue(presence.contains("StatusEffectInstance.INFINITE"));
        assertTrue(presence.contains("current.isInfinite()"));
        assertTrue(presence.contains("StatusEffects.INVISIBILITY"));
        assertTrue(presence.contains("NO_COLLISION"));
        assertTrue(promotion.contains("WraithLifecycle.promotePlayer(player, role)"));
        assertTrue(lifecycle.contains("WraithPresence.removeRestrictedEffects(player)"));
        assertFalse(between(lifecycle, "public static void promotePlayer", "public static void clearPlayer")
                .contains("WraithPresence.clear"));
        assertTrue(ownedMixin.contains("removeStatusEffectInternal"));
        assertTrue(ownedMixin.contains("clearStatusEffects"));
    }

    @Test
    void restrictedInteractionAllowlistAndTaskOnlyMoodAreWired() throws Exception {
        String participation = source("roles/special/wraith/runtime/WraithParticipation.java");
        String mood = source("mixin/WraithPlayerMoodComponentMixin.java");
        assertTrue(participation.contains("block instanceof FoodPlatterBlock"));
        assertTrue(participation.contains("block instanceof DrinkTrayBlock"));
        assertTrue(participation.contains("block instanceof BedBlock"));
        assertTrue(participation.contains("DataComponentTypes.FOOD"));
        assertTrue(participation.contains("CocktailItem"));
        assertTrue(mood.contains("WraithProgression.tick"));
        assertTrue(mood.contains("ci.cancel()"));
    }

    @Test
    void reconnectPreservesCadenceAndAdminSpectatorAuthority() throws Exception {
        String lifecycle = source("roles/special/wraith/runtime/WraithLifecycle.java");
        String progression = source("roles/special/wraith/progression/WraithProgression.java");
        String tasks = source("roles/special/wraith/progression/WraithTaskRuntime.java");
        String presence = source("roles/special/wraith/runtime/WraithPresence.java");
        assertTrue(lifecycle.contains("game.isRunning()"));
        assertTrue(lifecycle.contains("game.hasAnyRole(uuid)"));
        assertTrue(lifecycle.contains("game.isPlayerDead(uuid)"));
        assertTrue(progression.contains("ServerPlayConnectionEvents.DISCONNECT"));
        assertTrue(tasks.contains("WraithTaskSnapshot.capture(player)"));
        assertFalse(presence.contains("changeGameMode"));
        assertTrue(lifecycle.contains("if (player.isSpectator())"));
        assertTrue(lifecycle.contains("GameMode.ADVENTURE"));
    }

    @Test
    void activeIsolationAndCleanupHaveOneOwnerWithoutChatOrInventoryLocks() throws Exception {
        String participation = source("roles/special/wraith/runtime/WraithParticipation.java");
        String lifecycle = source("roles/special/wraith/runtime/WraithLifecycle.java");
        assertTrue(participation.contains("SparkFactionApi.registerPlayerAffectPolicy"));
        assertTrue(lifecycle.contains("SparkTraitsWraithBridge.clear(player, false)"));
        assertTrue(lifecycle.contains("SparkTraitsWraithBridge.clear(player, true)"));
        assertFalse(Files.exists(Path.of("src/main/java/dev/caecorthus/sparkwitch/mixin/WraithChatRestrictionMixin.java")));
        assertFalse(Files.exists(Path.of("src/main/java/dev/caecorthus/sparkwitch/mixin/WraithInventoryKeyMixin.java")));
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertTrue(startIndex >= 0, start);
        assertTrue(endIndex > startIndex, end);
        return source.substring(startIndex, endIndex);
    }

    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/dev/caecorthus/sparkwitch", relative));
    }
}
