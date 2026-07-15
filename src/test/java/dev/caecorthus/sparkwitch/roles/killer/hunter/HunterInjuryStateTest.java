package dev.caecorthus.sparkwitch.roles.killer.hunter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HunterInjuryStateTest {
    @Test
    void fractureLayersExpireIndependentlyAndStopAtFive() {
        HunterInjuryState state = new HunterInjuryState();
        assertEquals(HunterInjuryState.FractureApplication.ADDED, state.addFractureLayer(2));
        state.tick();
        assertEquals(HunterInjuryState.FractureApplication.ADDED, state.addFractureLayer(3));
        assertEquals(2, state.fractureLayers());

        state.tick();
        assertEquals(1, state.fractureLayers());
        state.tick();
        assertEquals(1, state.fractureLayers());
        state.tick();
        assertEquals(0, state.fractureLayers());

        for (int i = 0; i < HunterRules.MAX_FRACTURE_LAYERS; i++) {
            assertEquals(HunterInjuryState.FractureApplication.ADDED,
                    state.addFractureLayer(HunterRules.FRACTURE_LAYER_TICKS));
        }
        assertEquals(HunterInjuryState.FractureApplication.AT_CAP,
                state.addFractureLayer(HunterRules.FRACTURE_LAYER_TICKS));
    }

    @Test
    void boneSettingConsumesItselfInsteadOfAddingAFractureLayer() {
        HunterInjuryState state = new HunterInjuryState();

        assertEquals(HunterInjuryState.FractureApplication.CONSUMED_BONE_SETTING,
                state.addFractureLayerWithBoneSetting(HunterRules.FRACTURE_LAYER_TICKS));
        assertEquals(0, state.fractureLayers());
    }

    @Test
    void healingRemovesExactlyOneLayerAndRootUsesTheLongerRemainingDuration() {
        HunterInjuryState state = new HunterInjuryState();
        state.addFractureLayer(10);
        state.addFractureLayer(20);

        assertTrue(state.healOneFractureLayer());
        assertEquals(1, state.fractureLayers());
        assertTrue(state.healOneFractureLayer());
        assertFalse(state.healOneFractureLayer());

        state.rootFor(10);
        state.tick();
        state.rootFor(5);
        assertEquals(9, state.rootTicks());
        state.rootFor(20);
        assertEquals(20, state.rootTicks());
    }
}
