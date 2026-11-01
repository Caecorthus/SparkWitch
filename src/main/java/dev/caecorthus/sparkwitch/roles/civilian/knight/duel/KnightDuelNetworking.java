package dev.caecorthus.sparkwitch.roles.civilian.knight.duel;

import dev.caecorthus.sparkwitch.net.KnightDuelStopSoundS2CPayload;
import dev.caecorthus.sparkwitch.net.KnightDuelTargetC2SPayload;
import dev.caecorthus.sparkwitch.net.KnightDuelUiLockS2CPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Duel payload types and the server receiver. The duel state itself reaches clients only through the synced
 * {@link KnightDuelWorldComponent}.
 * 决斗数据包类型与服务端接收器。决斗状态本身只通过同步的 {@link KnightDuelWorldComponent} 下发给客户端。
 */
public final class KnightDuelNetworking {
    private KnightDuelNetworking() {
    }

    public static void registerServer() {
        PayloadTypeRegistry.playC2S().register(KnightDuelTargetC2SPayload.ID, KnightDuelTargetC2SPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(KnightDuelStopSoundS2CPayload.ID, KnightDuelStopSoundS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(KnightDuelUiLockS2CPayload.ID, KnightDuelUiLockS2CPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(KnightDuelTargetC2SPayload.ID, (payload, context) ->
                context.server().execute(() -> KnightDuelService.requestDuel(context.player(), payload.target())));
    }
}
