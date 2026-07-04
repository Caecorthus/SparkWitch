package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.impl.MurderousWitchDeathRayService;
import dev.caecorthus.sparkwitch.impl.WitchSkillUseService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public final class SparkWitchPackets {
    private static boolean registered;

    private SparkWitchPackets() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PayloadTypeRegistry.playC2S().register(UseWitchSkillC2SPacket.ID, UseWitchSkillC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(FireDeathRayC2SPacket.ID, FireDeathRayC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(
                SparkWitchServerConfirmS2CPacket.ID,
                SparkWitchServerConfirmS2CPacket.CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(UseWitchSkillC2SPacket.ID,
                (payload, context) -> WitchSkillUseService.use(context.player(), payload.targetUuid()));
        ServerPlayNetworking.registerGlobalReceiver(FireDeathRayC2SPacket.ID,
                (payload, context) -> MurderousWitchDeathRayService.fire(context.player()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            if (!ServerPlayNetworking.canSend(player, SparkWitchServerConfirmS2CPacket.ID)) {
                SparkWitch.LOGGER.warn(
                        "SparkWitch play confirmation channel {} is not available for {}.",
                        SparkWitchServerConfirmS2CPacket.PAYLOAD_ID,
                        player.getGameProfile().getName()
                );
                return;
            }

            // Reconfirm the SparkWitch server after play starts, because proxies can drop login queries.
            // 进入 play 阶段后再次确认 SparkWitch 服务端，因为代理可能吞掉登录查询。
            sender.sendPacket(new SparkWitchServerConfirmS2CPacket(SparkWitchVersionHandshake.localVersion()));
        });
    }
}
