package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.impl.MurderousWitchDeathRayService;
import dev.caecorthus.sparkwitch.impl.WitchSkillUseService;
import dev.caecorthus.sparkwitch.impl.NoellesRoleEnhancementService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

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
        PayloadTypeRegistry.playC2S().register(SelectCriminologistTargetC2SPacket.ID, SelectCriminologistTargetC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenCriminologistScreenS2CPacket.ID, OpenCriminologistScreenS2CPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(UseWitchSkillC2SPacket.ID,
                (payload, context) -> WitchSkillUseService.use(context.player(), payload.targetUuid()));
        ServerPlayNetworking.registerGlobalReceiver(FireDeathRayC2SPacket.ID,
                (payload, context) -> MurderousWitchDeathRayService.fire(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(SelectCriminologistTargetC2SPacket.ID,
                (payload, context) -> NoellesRoleEnhancementService.handleCriminologistSelection(
                        context.player(),
                        payload.victimUuid(),
                        payload.suspectUuid()
                ));
    }
}
