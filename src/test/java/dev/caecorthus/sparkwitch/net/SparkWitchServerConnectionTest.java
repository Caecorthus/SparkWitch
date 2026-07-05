package dev.caecorthus.sparkwitch.net;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkWitchServerConnectionTest {
    @AfterEach
    void reset() {
        SparkWitchServerConnection.reset();
    }

    @Test
    void ordinaryServersStartUnconfirmed() {
        SparkWitchServerConnection.reset();

        assertFalse(SparkWitchServerConnection.isConfirmedServer());
    }

    @Test
    void loginQueryConfirmsSparkWitchServerUntilDisconnect() {
        SparkWitchServerConnection.confirmServer();

        assertTrue(SparkWitchServerConnection.isConfirmedServer());

        SparkWitchServerConnection.reset();

        assertFalse(SparkWitchServerConnection.isConfirmedServer());
    }

    @Test
    void incompatibleConfirmationClearsStaleState() {
        SparkWitchServerConnection.confirmServer();

        assertFalse(SparkWitchServerConnection.confirmCompatible("0.1.5.2", "0.1.5.3"));
        assertFalse(SparkWitchServerConnection.isConfirmedServer());
    }

    @Test
    void compatibleConfirmationSetsConfirmedState() {
        SparkWitchServerConnection.reset();

        assertTrue(SparkWitchServerConnection.confirmCompatible("0.1.5.3", "0.1.5.3"));
        assertTrue(SparkWitchServerConnection.isConfirmedServer());
    }
}
