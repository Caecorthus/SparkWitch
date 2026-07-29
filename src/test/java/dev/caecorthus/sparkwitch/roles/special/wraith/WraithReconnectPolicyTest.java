package dev.caecorthus.sparkwitch.roles.special.wraith;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WraithReconnectPolicyTest {
    @Test
    void everyPersistedActiveWraithTerminatesOnJoinInsteadOfResuming() {
        assertTrue(WraithReconnectPolicy.shouldTerminateOnJoin(true));
        assertFalse(WraithReconnectPolicy.shouldTerminateOnJoin(false));
    }
}
