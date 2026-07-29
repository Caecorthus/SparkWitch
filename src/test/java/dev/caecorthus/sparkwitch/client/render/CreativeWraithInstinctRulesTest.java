package dev.caecorthus.sparkwitch.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class CreativeWraithInstinctRulesTest {
    @Test
    void creativeModeDoesNotGrantWraithInstinct() {
        assertFalse(CreativeWraithInstinctRules.shouldReveal(true, true, false, false, true, true));
        assertFalse(CreativeWraithInstinctRules.shouldReveal(false, true, false, false, true, true));
        assertFalse(CreativeWraithInstinctRules.shouldReveal(true, false, false, false, true, true));
        assertFalse(CreativeWraithInstinctRules.shouldReveal(true, true, true, false, true, true));
        assertFalse(CreativeWraithInstinctRules.shouldReveal(true, true, false, true, true, true));
        assertFalse(CreativeWraithInstinctRules.shouldReveal(true, true, false, false, false, true));
        assertFalse(CreativeWraithInstinctRules.shouldReveal(true, true, false, false, true, false));
    }
}
