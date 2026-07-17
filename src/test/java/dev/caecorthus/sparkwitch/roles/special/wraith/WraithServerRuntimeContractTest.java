package dev.caecorthus.sparkwitch.roles.special.wraith;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithServerRuntimeContractTest {
    @Test
    void persistentEffectsAndPromotionRemoveOnlyRestrictedEffects() throws Exception {
        String effects = source("roles/special/wraith/WraithEffectService.java");
        String promotion = source("roles/special/wraith/WraithPromotionService.java");
        String ownedMixin = source("mixin/WraithOwnedEffectMixin.java");
        assertTrue(effects.contains("StatusEffectInstance.INFINITE"));
        assertTrue(effects.contains("current.isInfinite()"));
        assertTrue(effects.contains("StatusEffects.INVISIBILITY"));
        assertTrue(effects.contains("NO_COLLISION"));
        assertTrue(promotion.contains("WraithEffectService.removeRestrictedEffects(player)"));
        assertFalse(promotion.contains("removeAll"));
        assertTrue(ownedMixin.contains("removeStatusEffectInternal"));
        assertTrue(ownedMixin.contains("clearStatusEffects"));
    }

    @Test
    void restrictedInteractionAllowlistAndTaskOnlyMoodAreWired() throws Exception {
        String interactions = source("roles/special/wraith/WraithInteractionService.java");
        String mood = source("mixin/WraithPlayerMoodComponentMixin.java");
        assertTrue(interactions.contains("block instanceof FoodPlatterBlock"));
        assertTrue(interactions.contains("block instanceof DrinkTrayBlock"));
        assertTrue(interactions.contains("block instanceof BedBlock"));
        assertTrue(interactions.contains("DataComponentTypes.FOOD"));
        assertTrue(interactions.contains("CocktailItem"));
        assertTrue(mood.contains("WraithTaskService.tick"));
        assertTrue(mood.contains("ci.cancel()"));
    }

    @Test
    void reconnectPreservesCadenceAndAdminSpectatorAuthority() throws Exception {
        String session = source("roles/special/wraith/WraithSessionService.java");
        String tasks = source("roles/special/wraith/WraithTaskService.java");
        String runtime = source("roles/special/wraith/WraithRuntimeStateService.java");
        String gameMode = source("roles/special/wraith/WraithGameModeService.java");
        assertTrue(session.contains("game.isRunning()"));
        assertTrue(session.contains("game.hasAnyRole(uuid)"));
        assertTrue(session.contains("game.isPlayerDead(uuid)"));
        assertTrue(tasks.contains("ServerPlayConnectionEvents.DISCONNECT"));
        assertTrue(tasks.contains("WraithTaskSnapshot.capture(handler.player)"));
        assertFalse(runtime.contains("changeGameMode"));
        assertTrue(gameMode.contains("if (player.isSpectator())"));
        assertTrue(gameMode.contains("GameMode.ADVENTURE"));
    }

    @Test
    void activeIsolationAndCleanupHaveOneOwnerWithoutChatOrInventoryLocks() throws Exception {
        String isolation = source("roles/special/wraith/WraithPlayerIsolationService.java");
        String session = source("roles/special/wraith/WraithSessionService.java");
        assertTrue(isolation.contains("SparkFactionApi.registerPlayerAffectPolicy"));
        assertTrue(session.contains("SparkTraitsWraithBridge.clear(player, false)"));
        assertTrue(session.contains("SparkTraitsWraithBridge.clear(player, true)"));
        assertFalse(Files.exists(Path.of("src/main/java/dev/caecorthus/sparkwitch/mixin/WraithChatRestrictionMixin.java")));
        assertFalse(Files.exists(Path.of("src/main/java/dev/caecorthus/sparkwitch/mixin/WraithInventoryKeyMixin.java")));
    }

    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/dev/caecorthus/sparkwitch", relative));
    }
}
