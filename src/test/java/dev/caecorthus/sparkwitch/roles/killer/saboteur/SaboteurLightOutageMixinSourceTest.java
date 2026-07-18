package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaboteurLightOutageMixinSourceTest {
    private static final Path MIXIN = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/mixin/saboteur/WorldBlackoutDetailsSaboteurMixin.java");
    private static final Path SERVICE = Path.of(
            "src/main/java/dev/caecorthus/sparkwitch/roles/killer/saboteur/SaboteurLightOutageService.java");
    private static final Path MIXIN_CONFIG = Path.of("src/main/resources/sparkwitch.mixins.json");

    @Test
    void hooksOnlyWathePerLampLifecycleAndFinalFlickerWrites() throws IOException {
        String mixin = Files.readString(MIXIN);

        assertTrue(mixin.contains("@Mixin(WorldBlackoutComponent.BlackoutDetails.class)"));
        assertTrue(mixin.contains("method = \"init\""));
        assertTrue(mixin.contains("method = \"tick\""));
        assertTrue(mixin.contains("method = \"end\""));
        assertTrue(mixin.contains("@Inject(method = \"end\", at = @At(\"RETURN\"))"));
        assertTrue(mixin.contains("sparkwitch$coordinatedEnd"));
        assertTrue(mixin.contains("coordinatesLampRestoration"));
        assertTrue(mixin.contains("protectWatheFlicker"));
        assertFalse(mixin.contains("WorldBlackoutComponent.KEY"));
        assertFalse(mixin.contains("triggerBlackout"));
    }

    @Test
    void localServiceDoesNotBorrowGlobalBlackoutEffectsOrCooldown() throws IOException {
        String service = Files.readString(SERVICE);

        assertTrue(service.contains("SaboteurRules.LIGHT_RADIUS"));
        assertTrue(service.contains("SaboteurRules.LIGHT_DURATION_TICKS"));
        assertTrue(service.contains("Properties.LIT"));
        assertTrue(service.contains("WatheProperties.ACTIVE"));
        assertFalse(service.contains("WatheBlocks"));
        assertFalse(service.contains("isPoweredBeforeOutages"));
        assertFalse(service.contains("triggerBlackout"));
        assertFalse(service.contains("StatusEffects"));
    }

    @Test
    void unloadEndsOnlyAnOutageEntangledWithEphemeralLocalState() throws IOException {
        String service = Files.readString(SERVICE);

        assertTrue(service.contains("runtime.state.hasLocalWatheOverlap()"));
        assertTrue(service.contains("WorldBlackoutComponent.KEY.get(world).reset()"));
        assertTrue(service.indexOf("runtime.state.hasLocalWatheOverlap()")
                < service.indexOf("RUNTIMES.remove(world)"));
    }

    @Test
    void registersTheRequiredServerMixin() throws IOException {
        assertTrue(Files.readString(MIXIN_CONFIG)
                .contains("\"saboteur.WorldBlackoutDetailsSaboteurMixin\""));
    }
}
