package dev.caecorthus.sparkwitch.client.curser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurserHudRulesTest {
    @Test
    void showsOnlyForConfirmedExactActivePromotedCurserRegardlessOfWatheAliveState() {
        assertTrue(CurserHudRules.shouldRender(true, true, true, true));

        assertFalse(CurserHudRules.shouldRender(false, true, true, true));
        assertFalse(CurserHudRules.shouldRender(true, false, true, true));
        assertFalse(CurserHudRules.shouldRender(true, true, false, true));
        assertFalse(CurserHudRules.shouldRender(true, true, true, false));
    }
}
