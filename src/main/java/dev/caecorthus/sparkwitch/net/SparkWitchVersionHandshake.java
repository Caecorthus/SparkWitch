package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class SparkWitchVersionHandshake {
    public static final Identifier VERSION_CHECK_ID = SparkWitch.id("version_check");

    private static boolean serverRegistered;

    private SparkWitchVersionHandshake() {
    }

    public static synchronized void registerServer() {
        if (serverRegistered) {
            return;
        }
        serverRegistered = true;
        SparkWitch.LOGGER.info(
                "Registering SparkWitch login version check on channel {}.",
                VERSION_CHECK_ID
        );

        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            String serverVersion = localVersion();
            SparkWitch.LOGGER.info(
                    "Sending SparkWitch login version query {} to {} with server version {}.",
                    VERSION_CHECK_ID,
                    handler.getConnectionInfo(),
                    serverVersion
            );
            ServerLoginNetworking.registerReceiver(handler, VERSION_CHECK_ID,
                    (minecraftServer, networkHandler, understood, buf, loginSynchronizer, responseSender) ->
                            handleResponse(networkHandler, understood, buf, serverVersion));
            sender.sendPacket(VERSION_CHECK_ID, writeVersion(serverVersion));
        });
    }

    public static String localVersion() {
        return FabricLoader.getInstance()
                .getModContainer(SparkWitch.MOD_ID)
                .orElseThrow()
                .getMetadata()
                .getVersion()
                .getFriendlyString();
    }

    public static PacketByteBuf writeVersion(String version) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(version);
        return buf;
    }

    public static String readVersion(PacketByteBuf buf) {
        return buf.readString();
    }

    private static void handleResponse(
            ServerLoginNetworkHandler handler,
            boolean understood,
            PacketByteBuf buf,
            String serverVersion
    ) {
        // Reject before witch skill packets or role components see mixed jar versions.
        // 在女巫技能封包或角色组件看到混用 jar 版本前拒绝连接。
        if (!understood) {
            SparkWitch.LOGGER.warn(
                    "SparkWitch login version query {} was not understood by {}. Expected client version {}.",
                    VERSION_CHECK_ID,
                    handler.getConnectionInfo(),
                    serverVersion
            );
            handler.disconnect(Text.literal(SparkWitchVersionCheck.missingClientMessage(serverVersion)));
            return;
        }

        String clientVersion = readVersion(buf);
        SparkWitch.LOGGER.info(
                "Received SparkWitch login version response from {}: client={}, server={}.",
                handler.getConnectionInfo(),
                clientVersion,
                serverVersion
        );
        if (!SparkWitchVersionCheck.isCompatible(serverVersion, clientVersion)) {
            SparkWitch.LOGGER.warn(
                    "Rejecting SparkWitch version mismatch for {}: client={}, server={}.",
                    handler.getConnectionInfo(),
                    clientVersion,
                    serverVersion
            );
            handler.disconnect(Text.literal(SparkWitchVersionCheck.mismatchMessage(serverVersion, clientVersion)));
        }
    }
}
