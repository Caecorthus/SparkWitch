package dev.caecorthus.sparkwitch.roles.civilian.blind.net;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * The single send path for {@link BlindPulseS2CPayload}; skipped when the client cannot receive it. Callers have
 * already decided that this Blind may perceive the pulse.
 * {@link BlindPulseS2CPayload} 的唯一发送路径；客户端无法接收时跳过。调用方已判定该盲人可以感知此脉冲。
 */
public final class BlindPulseSender {
    private BlindPulseSender() {
    }

    public static void send(ServerPlayerEntity blind, BlindPulseS2CPayload payload) {
        if (blind != null && payload != null && ServerPlayNetworking.canSend(blind, BlindPulseS2CPayload.ID)) {
            ServerPlayNetworking.send(blind, payload);
        }
    }
}
