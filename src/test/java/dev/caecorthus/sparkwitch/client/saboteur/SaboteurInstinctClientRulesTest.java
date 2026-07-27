package dev.caecorthus.sparkwitch.client.saboteur;

import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurRole;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaboteurInstinctClientRulesTest {
    @Test
    void extendsAFalseNativeGateOnlyForConfirmedExactActivePromotedSaboteurHoldingInstinct() {
        Identifier saboteur = SaboteurRole.ID;

        assertTrue(SaboteurInstinctClientRules.shouldEnableNativeInstinct(
                false, saboteur, true, true, true, true));
        assertTrue(SaboteurInstinctClientRules.shouldEnableNativeInstinct(
                true, null, false, false, false, false));

        assertFalse(SaboteurInstinctClientRules.shouldEnableNativeInstinct(
                false, saboteur, false, true, true, true));
        assertFalse(SaboteurInstinctClientRules.shouldEnableNativeInstinct(
                false, Identifier.of("sparkwitch", "curser"), true, true, true, true));
        assertFalse(SaboteurInstinctClientRules.shouldEnableNativeInstinct(
                false, saboteur, true, false, true, true));
        assertFalse(SaboteurInstinctClientRules.shouldEnableNativeInstinct(
                false, saboteur, true, true, false, true));
        assertFalse(SaboteurInstinctClientRules.shouldEnableNativeInstinct(
                false, saboteur, true, true, true, false));
        assertFalse(SaboteurInstinctClientRules.shouldEnableNativeInstinct(
                false, null, true, true, true, true));
    }
}
