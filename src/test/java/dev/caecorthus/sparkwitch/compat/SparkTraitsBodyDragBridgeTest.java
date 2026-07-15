package dev.caecorthus.sparkwitch.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkTraitsBodyDragBridgeTest {
    @Test
    void permitsBaseBodiesWhenSparkTraitsIsAbsent() {
        assertTrue(SparkTraitsBodyDragBridge.canDragFromQuery(false, null));
    }

    @Test
    void failsClosedWhenLoadedApiIsUnavailableOrReportsFakeBody() {
        assertFalse(SparkTraitsBodyDragBridge.canDragFromQuery(true, null));
        assertFalse(SparkTraitsBodyDragBridge.canDragFromQuery(true, true));
        assertTrue(SparkTraitsBodyDragBridge.canDragFromQuery(true, false));
    }
}
