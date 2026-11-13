package dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Stable packet contract: registers every BEAR smoke payload type (the only place that does), from
 * {@code SparkWitchPackets.register()} on both sides. All four are S2C and visual only: the client draws smoke from
 * them, and no server rule (line of sight included, owner 2026-11-05) reads smoke. The receivers live in the client
 * source set ({@code client/bear/smoke/SmokeNetworkClient}). Sends skip players whose client lacks the channel.
 * 稳定数据包契约：注册全部 BEAR 烟雾数据包类型（唯一注册处），由双端的 {@code SparkWitchPackets.register()} 调用。
 * 四个都是 S2C 且纯视觉：客户端据此绘制烟雾，任何服务端规则（包括视线，所有者 2026-11-05）都不读取烟雾。接收器位于客户端
 * 源码集（{@code client/bear/smoke/SmokeNetworkClient}）。客户端没有该通道的玩家会被跳过。
 */
public final class SmokeNetworking {
    private static boolean registered;

    private SmokeNetworking() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PayloadTypeRegistry.playS2C().register(SmokeCloudS2CPayload.ID, SmokeCloudS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SmokeCloudRemoveS2CPayload.ID, SmokeCloudRemoveS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SmokeHoleS2CPayload.ID, SmokeHoleS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SmokeClearS2CPayload.ID, SmokeClearS2CPayload.CODEC);
    }

    /** Sends to every player in the world. / 发给该世界内的所有玩家。 */
    public static void sendToWorld(ServerWorld world, CustomPayload payload) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            send(player, payload);
        }
    }

    public static void send(ServerPlayerEntity player, CustomPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.getId())) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
