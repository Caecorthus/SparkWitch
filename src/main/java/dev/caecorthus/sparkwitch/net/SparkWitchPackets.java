package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.OrthopedistSkillService;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.UseOrthopedistSkillC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.guardianangel.GuardianAngelFeatureService;
import dev.caecorthus.sparkwitch.roles.civilian.guardianangel.UseGuardianAngelSkillC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.UseVendettaKnifeC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaKnifeService;
import dev.caecorthus.sparkwitch.roles.civilian.tarotreader.TarotReaderDivinationService;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurNetworking;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperThrowService;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.FocusedFootstepsRequestService;
import dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchDeathRay.MurderousWitchDeathRayService;
import dev.caecorthus.sparkwitch.roles.witch.curser.CurserFeatureService;
import dev.caecorthus.sparkwitch.roles.witch.curser.UseCurserAbilityC2SPacket;
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
        SaboteurNetworking.register();
        PayloadTypeRegistry.playC2S().register(UseWitchSkillC2SPacket.ID, UseWitchSkillC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(FireDeathRayC2SPacket.ID, FireDeathRayC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(UseCurserAbilityC2SPacket.ID, UseCurserAbilityC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(
                UseOrthopedistSkillC2SPacket.ID,
                UseOrthopedistSkillC2SPacket.CODEC
        );
        PayloadTypeRegistry.playC2S().register(
                ThrowKidnapperBodyC2SPacket.ID,
                ThrowKidnapperBodyC2SPacket.CODEC
        );
        PayloadTypeRegistry.playC2S().register(
                UseGuardianAngelSkillC2SPacket.ID,
                UseGuardianAngelSkillC2SPacket.CODEC
        );
        PayloadTypeRegistry.playC2S().register(
                UseVendettaKnifeC2SPacket.ID,
                UseVendettaKnifeC2SPacket.CODEC
        );
        PayloadTypeRegistry.playC2S().register(
                SubmitTarotDivinationSelectionC2SPacket.ID,
                SubmitTarotDivinationSelectionC2SPacket.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                SparkWitchServerConfirmS2CPacket.ID,
                SparkWitchServerConfirmS2CPacket.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                TarotDivinationSnapshotS2CPacket.ID,
                TarotDivinationSnapshotS2CPacket.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                OpenTarotDivinationSelectorS2CPacket.ID,
                OpenTarotDivinationSelectorS2CPacket.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                OpenBlackRavenLedgerS2CPacket.ID,
                OpenBlackRavenLedgerS2CPacket.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                WraithRoleAnnouncementS2CPacket.ID,
                WraithRoleAnnouncementS2CPacket.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                FocusedFootstepsUseResultS2CPacket.ID,
                FocusedFootstepsUseResultS2CPacket.CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(UseWitchSkillC2SPacket.ID,
                (payload, context) -> FocusedFootstepsRequestService.use(
                        context.player(), payload.targetUuid()));
        ServerPlayNetworking.registerGlobalReceiver(FireDeathRayC2SPacket.ID,
                (payload, context) -> MurderousWitchDeathRayService.fire(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(UseCurserAbilityC2SPacket.ID,
                (payload, context) -> CurserFeatureService.use(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(UseOrthopedistSkillC2SPacket.ID,
                (payload, context) -> OrthopedistSkillService.use(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(ThrowKidnapperBodyC2SPacket.ID,
                (payload, context) -> KidnapperThrowService.throwCarriedBody(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(UseGuardianAngelSkillC2SPacket.ID,
                (payload, context) -> GuardianAngelFeatureService.use(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(UseVendettaKnifeC2SPacket.ID,
                (payload, context) -> VendettaKnifeService.use(
                        context.player(), payload.targetEntityId()));
        ServerPlayNetworking.registerGlobalReceiver(SubmitTarotDivinationSelectionC2SPacket.ID,
                (payload, context) -> TarotReaderDivinationService.submit(context.player(), payload));
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
