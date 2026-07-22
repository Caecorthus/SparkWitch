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
        String promotionService = source("roles/special/wraith/progression/WraithPromotionService.java");
        String ownedMixin = source("mixin/WraithOwnedEffectMixin.java");
        assertTrue(presence.contains("StatusEffectInstance.INFINITE"));
        assertTrue(presence.contains("current.isInfinite()"));
        assertTrue(presence.contains("StatusEffects.INVISIBILITY"));
        assertTrue(presence.contains("NO_COLLISION"));
        assertTrue(promotion.contains("WraithPromotionService.promote(player"));
        assertTrue(promotionService.contains("WraithLifecycle.promotePlayer(player, role)"));
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
    void lifecycleTransitionsReleaseSleepingStateThroughVanillaWakeUp() throws Exception {
        String lifecycle = source("roles/special/wraith/runtime/WraithLifecycle.java");
        assertTrue(lifecycle.contains("private static void wakeIfSleeping(ServerPlayerEntity player)"));
        assertTrue(lifecycle.contains("if (player.isSleeping())"));
        assertTrue(lifecycle.contains("player.wakeUp(true, true)"));
        assertTrue(between(lifecycle, "public static void activateConvertedPlayer", "public static void promotePlayer")
                .indexOf("wakeIfSleeping(player)")
                < between(lifecycle, "public static void activateConvertedPlayer", "public static void promotePlayer")
                .indexOf("transitionRole(player"));
        assertTrue(between(lifecycle, "public static void promotePlayer", "public static void clearPlayer")
                .indexOf("wakeIfSleeping(player)")
                < between(lifecycle, "public static void promotePlayer", "public static void clearPlayer")
                .indexOf("transitionRole(player"));
        String clearPlayer = between(lifecycle, "public static void clearPlayer", "public static void clearRoundState");
        assertTrue(clearPlayer.contains("if (wasActive)"));
        assertTrue(clearPlayer.contains("wakeIfSleeping(player)"));
        assertTrue(between(lifecycle, "private static void onJoin", "private static void tickWorld")
                .contains("wakeIfSleeping(player)"));
        assertFalse(lifecycle.contains("setPose(EntityPose.STANDING)"));
    }

    @Test
    void activeIsolationAndCleanupHaveOneOwnerWithoutChatOrInventoryLocks() throws Exception {
        String participation = source("roles/special/wraith/runtime/WraithParticipation.java");
        String lifecycle = source("roles/special/wraith/runtime/WraithLifecycle.java");
        assertTrue(participation.contains("SparkFactionApi.registerPlayerAffectPolicy"));
        assertTrue(participation.contains("SparkFactionApi.registerEntityCollisionExemption"));
        assertTrue(participation.contains("entity instanceof PlayerEntity player"));
        assertTrue(participation.contains("WraithStateService.isActive(player)"));
        assertTrue(participation.contains("isWindSpiritProjectile(actionId, actor)"));
        assertTrue(participation.contains("Identifier.of(\"sparkfactionapi\", \"projectile\")"));
        assertTrue(participation.contains("WindSpiritRules.isActivePromotedWindSpirit(actor)"));
        assertTrue(participation.contains("actor.getUuid().equals(target.getUuid())"));
        assertTrue(participation.contains("target.isAlive()"));
        assertTrue(participation.contains("GameFunctions.isPlayerPlayingAndAlive(target)"));
        assertTrue(participation.contains("target.isSpectator()"));
        assertTrue(participation.contains("WraithStateService.isActive(target)"));
        assertTrue(participation.contains("&& !targetActiveWraith"));
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
