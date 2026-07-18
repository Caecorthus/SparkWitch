package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PoisonAppleIntegrationSourceTest {
    private static final Path MAIN = Path.of("src/main/java/dev/caecorthus/sparkwitch");
    private static final Path CLIENT = Path.of("src/client/java/dev/caecorthus/sparkwitch");

    @Test
    void poisonAppleItemKeepsInteractionLogicInTheRoleModule() throws IOException {
        String source = read(MAIN, "roles/killer/witchmaiden/PoisonAppleItem.java");

        assertTrue(source.contains("final class PoisonAppleItem extends Item"));
        assertFalse(source.contains("use("));
        assertFalse(source.contains("useOnBlock("));
    }

    @Test
    void plateMixinPersistsAuthorityButStripsSensitiveChunkData() throws IOException {
        String source = read(MAIN, "mixin/witchmaiden/BeveragePlateBlockEntityPoisonAppleMixin.java");

        assertTrue(source.contains("method = \"writeNbt\""));
        assertTrue(source.contains("method = \"readNbt\""));
        assertTrue(source.contains("method = \"toInitialChunkDataNbt\""));
        assertTrue(source.contains("PoisonApplePlateNbt.stripForClient"));
        assertFalse(source.contains("setPoisoner("));
    }

    @Test
    void platterWrapperCountsOnlyEmptyToNonemptyMainHandTransitions() throws IOException {
        String source = read(MAIN, "mixin/witchmaiden/FoodPlatterBlockPoisonAppleMixin.java");
        String service = read(MAIN, "roles/killer/witchmaiden/PoisonApplePlateService.java");
        String bridge = read(MAIN, "compat/NoellesToxicologistBridge.java");

        assertTrue(source.contains("@WrapMethod"));
        assertTrue(source.contains("boolean handWasEmpty"));
        assertTrue(source.contains("handWasEmpty && !player.getMainHandStack().isEmpty()"));
        assertTrue(source.contains("PoisonApplePlateService.isHoldingPoisonApple(player)"));
        assertTrue(source.contains("PoisonApplePlateService.recordSuccessfulTake"));
        assertTrue(source.contains("PoisonApplePlateService.cureWithAntidote"));
        assertTrue(service.contains("NoellesToxicologistBridge.isExactToxicologist(role)"));
        assertTrue(service.contains("NoellesToxicologistBridge.holdsAntidote(player)"));
        assertTrue(service.contains("NoellesToxicologistBridge.isAntidoteCoolingDown(player)"));
        assertTrue(service.contains("PoisonAppleAntidoteRules.canCure"));
        assertFalse(service.contains("org.agmas.noellesroles"));
        assertTrue(bridge.contains("role == Noellesroles.TOXICOLOGIST"));
        assertTrue(bridge.contains("AntidoteItem.COOLDOWN_TICKS"));
    }

    @Test
    void blockEntityLifecycleRejectsLoadedOldMatchStateAndDoesNotRetainUnloadedPlates() throws IOException {
        String source = read(MAIN, "mixin/witchmaiden/BlockEntityPoisonAppleLifecycleMixin.java");

        assertTrue(source.contains("method = \"setWorld\""));
        assertTrue(source.contains("PoisonApplePlateService.refreshMatch(poisonApple)"));
        assertTrue(source.contains("method = \"markRemoved\""));
        assertTrue(source.contains("PoisonApplePlateTracker.untrack(poisonApple)"));
    }

    @Test
    void drinkMixinUsesStrictCocktailMarkerGateAndClearsBothPoisonFields() throws IOException {
        String source = read(MAIN, "mixin/witchmaiden/ItemStackPoisonAppleDrinkMixin.java");

        assertTrue(source.contains("getItem() instanceof CocktailItem"));
        assertTrue(source.contains("PoisonAppleDrinkMarker.isMarked"));
        assertTrue(source.contains("PoisonUtils.applyFoodPoison(player, stack)"));
        assertTrue(source.contains("PoisonAppleDrinkMarker.clear(stack)"));
        assertTrue(source.contains("stack.remove(WatheDataComponentTypes.POISONER)"));
    }

    @Test
    void clientParticlesReuseWathesVisibilityDecisionAndRiseAboveThePlate() throws IOException {
        String hooks = read(CLIENT, "client/witchmaiden/PoisonAppleParticleClientHooks.java");
        String mixin = read(CLIENT, "client/mixin/witchmaiden/BeveragePlateBlockEntityPoisonAppleParticleMixin.java");

        assertTrue(hooks.contains("WatheClient.isKiller()"));
        assertTrue(hooks.contains("WatheClient.canSeeSpectatorInformation()"));
        assertTrue(hooks.contains("CanSeePoison.EVENT.invoker().visible(player)"));
        assertTrue(mixin.contains("DustParticleEffect"));
        assertTrue(mixin.contains("sparkwitch$isPoisonAppleArmed()"));
        assertTrue(mixin.contains("PoisonAppleParticleClientHooks.canSee("));
        assertTrue(mixin.contains("new Vector3f(1.0f, 0.0f, 0.0f), 1.0f"));
        assertTrue(mixin.contains("nextBetween(0, 20) < 17"));
        assertTrue(mixin.contains("nextDouble() - 0.5D) * 0.3D"));
        assertTrue(mixin.contains("pos.getX() + 0.5D + offsetX"));
        assertTrue(mixin.contains("pos.getY() + 0.2D"));
        assertTrue(mixin.contains("pos.getZ() + 0.5D + offsetZ"));
        assertTrue(mixin.contains("0.15D"));
    }

    private static String read(Path root, String relativePath) throws IOException {
        return Files.readString(root.resolve(relativePath));
    }
}
