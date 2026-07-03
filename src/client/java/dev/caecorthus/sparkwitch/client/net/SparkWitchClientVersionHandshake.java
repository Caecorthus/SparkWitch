package dev.caecorthus.sparkwitch.client.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.net.SparkWitchVersionHandshake;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;

import java.util.concurrent.CompletableFuture;

public final class SparkWitchClientVersionHandshake {
    private static boolean clientRegistered;

    private SparkWitchClientVersionHandshake() {
    }

    public static synchronized void registerClient() {
        if (clientRegistered) {
            SparkWitch.LOGGER.info("SparkWitch client login version receiver is already registered.");
            return;
        }
        clientRegistered = true;

        boolean registered = ClientLoginNetworking.registerGlobalReceiver(SparkWitchVersionHandshake.VERSION_CHECK_ID,
                (client, handler, buf, callbacks) -> {
                    String serverVersion = SparkWitchVersionHandshake.readVersion(buf);
                    String clientVersion = SparkWitchVersionHandshake.localVersion();
                    SparkWitch.LOGGER.info(
                            "Answering SparkWitch login version query: server={}, client={}.",
                            serverVersion,
                            clientVersion
                    );
                    return CompletableFuture.completedFuture(
                            SparkWitchVersionHandshake.writeVersion(clientVersion));
                });
        if (registered) {
            // The server treats an unregistered receiver as a missing client-side mod.
            // 服务端会把未注册的接收器判定为客户端缺少该模组。
            SparkWitch.LOGGER.info(
                    "Registered SparkWitch client login version receiver on channel {}.",
                    SparkWitchVersionHandshake.VERSION_CHECK_ID
            );
        } else {
            SparkWitch.LOGGER.warn(
                    "SparkWitch client login version receiver already existed on channel {}.",
                    SparkWitchVersionHandshake.VERSION_CHECK_ID
            );
        }
    }
}
