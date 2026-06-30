package dev.caecorthus.sparkwitch.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrandWitchActiveSkillServiceTest {
    @Test
    void ceremonialSwordAutoSelectsOnlyHotbarSlots() {
        assertTrue(GrandWitchActiveSkillService.shouldAutoSelectCeremonialSwordSlot(0));
        assertTrue(GrandWitchActiveSkillService.shouldAutoSelectCeremonialSwordSlot(8));
        assertFalse(GrandWitchActiveSkillService.shouldAutoSelectCeremonialSwordSlot(-1));
        assertFalse(GrandWitchActiveSkillService.shouldAutoSelectCeremonialSwordSlot(9));
        assertFalse(GrandWitchActiveSkillService.shouldAutoSelectCeremonialSwordSlot(36));
    }
}
