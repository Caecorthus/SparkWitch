package dev.caecorthus.sparkwitch.client.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConfirmS2CPacket;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.net.SparkWitchVersionCheck;
import dev.caecorthus.sparkwitch.net.SparkWitchVersionHandshake;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.text.Text;

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
                    confirmSparkWitchServer(serverVersion, clientVersion, "login");
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

        ClientPlayNetworking.registerGlobalReceiver(SparkWitchServerConfirmS2CPacket.ID, (payload, context) -> {
            String clientVersion = SparkWitchVersionHandshake.localVersion();
            if (!SparkWitchVersionCheck.isCompatible(payload.serverVersion(), clientVersion)) {
                SparkWitch.LOGGER.warn(
                        "Disconnecting from SparkWitch play confirmation mismatch: server={}, client={}.",
                        payload.serverVersion(),
                        clientVersion
                );
                context.responseSender().disconnect(Text.literal(
                        SparkWitchVersionCheck.mismatchMessage(payload.serverVersion(), clientVersion)
                ));
                return;
            }
            confirmSparkWitchServer(payload.serverVersion(), clientVersion, "play");
        });
    }

    private static void confirmSparkWitchServer(String serverVersion, String clientVersion, String stage) {
        SparkWitchServerConnection.confirmServer();
        SparkWitchRoles.refreshAssassinGuessRoleOrder();
        SparkWitch.LOGGER.info(
                "Confirmed SparkWitch server through {} channel: server={}, client={}.",
                stage,
                serverVersion,
                clientVersion
        );
    }
}
