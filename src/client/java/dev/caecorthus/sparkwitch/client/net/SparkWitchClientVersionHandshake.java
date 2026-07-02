package dev.caecorthus.sparkwitch.client.net;

import dev.caecorthus.sparkwitch.net.SparkWitchVersionHandshake;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;

import java.util.concurrent.CompletableFuture;

public final class SparkWitchClientVersionHandshake {
    private static boolean clientRegistered;

    private SparkWitchClientVersionHandshake() {
    }

    public static synchronized void registerClient() {
        if (clientRegistered) {
            return;
        }
        clientRegistered = true;

        ClientLoginNetworking.registerGlobalReceiver(SparkWitchVersionHandshake.VERSION_CHECK_ID,
                (client, handler, buf, callbacks) -> {
                    SparkWitchVersionHandshake.readVersion(buf);
                    return CompletableFuture.completedFuture(
                            SparkWitchVersionHandshake.writeVersion(SparkWitchVersionHandshake.localVersion()));
                });
    }
}
