package dev.caecorthus.sparkwitch.skill;

import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitchSkillHudRulesTest {
    @Test
    void prophetCostReplacesReadyTextOnlyWhileReady() {
        assertTrue(WitchSkillHudRules.shouldShowProphetCoinCost(ProphetRules.DEATH_OMEN_ID, 0, 0));
        assertFalse(WitchSkillHudRules.shouldShowProphetCoinCost(ProphetRules.DEATH_OMEN_ID, 1, 0));
        assertFalse(WitchSkillHudRules.shouldShowProphetCoinCost(ProphetRules.DEATH_OMEN_ID, 0, 1));
        assertFalse(WitchSkillHudRules.shouldShowProphetCoinCost(null, 0, 0));
    }
}
