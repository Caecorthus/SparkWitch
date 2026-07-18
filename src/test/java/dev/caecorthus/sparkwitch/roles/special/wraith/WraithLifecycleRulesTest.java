package dev.caecorthus.sparkwitch.roles.special.wraith;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithLifecycleRulesTest {
    @Test
    void promotionQueuesAtThreeCompletionsExactlyOnce() {
        assertFalse(WraithLifecycleRules.shouldQueuePromotion(false, false, 3));
        assertFalse(WraithLifecycleRules.shouldQueuePromotion(true, false, 2));
        assertTrue(WraithLifecycleRules.shouldQueuePromotion(true, false, 3));
        assertFalse(WraithLifecycleRules.shouldQueuePromotion(true, true, 4));
    }

    @Test
    void isolationIsBidirectionalButAllowsSelfEffects() {
        assertTrue(WraithLifecycleRules.canAffectPlayer(true, true, true));
        assertFalse(WraithLifecycleRules.canAffectPlayer(true, false, false));
        assertFalse(WraithLifecycleRules.canAffectPlayer(false, true, false));
        assertTrue(WraithLifecycleRules.canAffectPlayer(false, false, false));
    }

    @Test
    void reconnectRequiresEveryRoundInvariant() {
        assertTrue(WraithLifecycleRules.shouldResume(true, true, true, true));
        assertFalse(WraithLifecycleRules.shouldResume(true, false, true, true));
        assertFalse(WraithLifecycleRules.shouldResume(true, true, false, true));
        assertFalse(WraithLifecycleRules.shouldResume(true, true, true, false));
    }
}
