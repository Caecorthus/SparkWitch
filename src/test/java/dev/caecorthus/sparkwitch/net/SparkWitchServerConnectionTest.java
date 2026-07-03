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
}
