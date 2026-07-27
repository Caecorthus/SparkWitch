package dev.caecorthus.sparkwitch.client.curser;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurserInstinctClientRulesTest {
    @Test
    void enablesWitchInstinctLightOnlyForConfirmedExactActivePromotedCurserHoldingInstinct() {
        Identifier curser = SparkWitchRoles.CURSER_ID;

        assertTrue(CurserInstinctClientRules.shouldUseWitchInstinctLight(
                curser, true, true, true, false, true));

        assertFalse(CurserInstinctClientRules.shouldUseWitchInstinctLight(
                curser, false, true, true, false, true));
        assertFalse(CurserInstinctClientRules.shouldUseWitchInstinctLight(
                Identifier.of("sparkwitch", "saboteur"), true, true, true, false, true));
        assertFalse(CurserInstinctClientRules.shouldUseWitchInstinctLight(
                curser, true, false, true, false, true));
        assertFalse(CurserInstinctClientRules.shouldUseWitchInstinctLight(
                curser, true, true, false, false, true));
        assertFalse(CurserInstinctClientRules.shouldUseWitchInstinctLight(
                curser, true, true, true, true, true));
        assertFalse(CurserInstinctClientRules.shouldUseWitchInstinctLight(
                curser, true, true, true, false, false));
        assertFalse(CurserInstinctClientRules.shouldUseWitchInstinctLight(
                null, true, true, true, false, true));
    }
}
