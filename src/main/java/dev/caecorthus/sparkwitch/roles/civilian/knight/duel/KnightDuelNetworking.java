package dev.caecorthus.sparkwitch.roles.civilian.knight.duel;

import dev.caecorthus.sparkwitch.net.KnightDuelStateS2CPayload;
import dev.caecorthus.sparkwitch.net.KnightDuelStopSoundS2CPayload;
import dev.caecorthus.sparkwitch.net.KnightDuelTargetC2SPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Duel payload types and the server receiver. / 决斗数据包类型与服务端接收器。 */
public final class KnightDuelNetworking {
    private KnightDuelNetworking() {
    }

    public static void registerServer() {
        PayloadTypeRegistry.playC2S().register(KnightDuelTargetC2SPayload.ID, KnightDuelTargetC2SPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(KnightDuelStopSoundS2CPayload.ID, KnightDuelStopSoundS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(KnightDuelStateS2CPayload.ID, KnightDuelStateS2CPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(KnightDuelTargetC2SPayload.ID, (payload, context) ->
                context.server().execute(() -> KnightDuelService.requestDuel(context.player(), payload.target())));
    }
}
