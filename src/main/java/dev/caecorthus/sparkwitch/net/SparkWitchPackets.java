package dev.caecorthus.sparkwitch.net;

import dev.caecorthus.sparkwitch.roles.civilian.blind.kit.BlindAttuneService;
import dev.caecorthus.sparkwitch.roles.civilian.blind.net.BlindPulseS2CPayload;
import dev.caecorthus.sparkwitch.roles.civilian.blind.net.UseBlindAttuneC2SPayload;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.launcher.PotionLauncherFireService;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.swordfish.SwordfishStabC2SPayload;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.swordfish.SwordfishStabService;
import dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaSkillService;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeRuntime;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetProphecyService;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.OrthopedistSkillService;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.UseOrthopedistSkillC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.guardianangel.GuardianAngelFeatureService;
import dev.caecorthus.sparkwitch.roles.civilian.guardianangel.UseGuardianAngelSkillC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.UseVendettaKnifeC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaKnifeService;
import dev.caecorthus.sparkwitch.roles.civilian.tarotreader.TarotReaderDivinationService;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseService;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurNetworking;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerNetworking;
import dev.caecorthus.sparkwitch.roles.civilian.usec.net.UsecNetworking;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.net.RiftwalkerNetworking;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperThrowService;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.FocusedFootstepsRequestService;
import dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchDeathRay.MurderousWitchDeathRayService;
import dev.caecorthus.sparkwitch.roles.witch.curser.CurserFeatureService;
import dev.caecorthus.sparkwitch.roles.witch.curser.UseCurserAbilityC2SPacket;
import dev.caecorthus.sparkwitch.roles.killer.magician.UseMagicianAbilityC2SPacket;
import dev.caecorthus.sparkwitch.roles.killer.magician.SelectMagicianTargetC2SPacket;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianAbility;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlayerComponent;
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
        SeekerNetworking.register();
        // Riftwalker payloads register in their own role-owned class, like the Seeker. / 隙行者数据包与搜寻者一样在自有类中注册。
        RiftwalkerNetworking.register();
        PayloadTypeRegistry.playC2S().register(OpenJudgeSelectionC2SPacket.ID, OpenJudgeSelectionC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ConfirmJudgeSelectionC2SPacket.ID, ConfirmJudgeSelectionC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenJudgeSelectionS2CPacket.ID, OpenJudgeSelectionS2CPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(OpenJudgeSelectionC2SPacket.ID,
                (payload, context) -> JudgeRuntime.openSelection(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(ConfirmJudgeSelectionC2SPacket.ID,
                (payload, context) -> JudgeRuntime.confirmSelection(context.player(), payload.sessionId(), payload.targetId()));
        // Prophecy uses its own session packets; the generic skill packet is refused for it.
        // 预言使用自有会话数据包；通用技能包对其一律拒绝。
        PayloadTypeRegistry.playC2S().register(RequestProphecyC2SPacket.ID, RequestProphecyC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ConfirmProphecyC2SPacket.ID, ConfirmProphecyC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenProphecyS2CPacket.ID, OpenProphecyS2CPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RequestProphecyC2SPacket.ID,
                (payload, context) -> ProphetProphecyService.requestSession(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(ConfirmProphecyC2SPacket.ID,
                (payload, context) -> ProphetProphecyService.confirmGuess(
                        context.player(), payload.sessionId(), payload.victim(), payload.groupId()));
        PayloadTypeRegistry.playC2S().register(UseWitchSkillC2SPacket.ID, UseWitchSkillC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(EmmaFactorC2SPacket.ID, EmmaFactorC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(UseMagicianAbilityC2SPacket.ID, UseMagicianAbilityC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SelectMagicianTargetC2SPacket.ID, SelectMagicianTargetC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(FireDeathRayC2SPacket.ID, FireDeathRayC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(FirePotionLauncherC2SPacket.ID, FirePotionLauncherC2SPacket.CODEC);
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
        PayloadTypeRegistry.playC2S().register(
                SwordfishStabC2SPayload.ID,
                SwordfishStabC2SPayload.CODEC
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
                TarotDivinationReadingS2CPacket.ID,
                TarotDivinationReadingS2CPacket.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                OpenBlackRavenLedgerS2CPacket.ID,
                OpenBlackRavenLedgerS2CPacket.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                OpenProphetNecrologyS2CPacket.ID,
                OpenProphetNecrologyS2CPacket.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                OpenBlackRavenDisguiseS2CPacket.ID,
                OpenBlackRavenDisguiseS2CPacket.CODEC
        );
        PayloadTypeRegistry.playC2S().register(
                SelectBlackRavenDisguiseC2SPacket.ID,
                SelectBlackRavenDisguiseC2SPacket.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                WraithRoleAnnouncementS2CPacket.ID,
                WraithRoleAnnouncementS2CPacket.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                FocusedFootstepsUseResultS2CPacket.ID,
                FocusedFootstepsUseResultS2CPacket.CODEC
        );
        // Blind: the Attune request (stun, Seeker and Fear guarded) and the owner-only perception pulse.
        // 盲人：凝神请求（受眩晕、搜寻者与恐惧拦截）与只发给本人的感知脉冲。
        PayloadTypeRegistry.playC2S().register(
                UseBlindAttuneC2SPayload.ID,
                UseBlindAttuneC2SPayload.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                BlindPulseS2CPayload.ID,
                BlindPulseS2CPayload.CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(UseWitchSkillC2SPacket.ID,
                (payload, context) -> FocusedFootstepsRequestService.use(
                        context.player(), payload.targetUuid()));
        ServerPlayNetworking.registerGlobalReceiver(EmmaFactorC2SPacket.ID,
                (payload, context) -> EmmaSkillService.use(context.player(), payload.targetId()));
        ServerPlayNetworking.registerGlobalReceiver(
                UseMagicianAbilityC2SPacket.ID,
                (payload, context) -> context.server().execute(
                        () -> MagicianAbility.handle(context.player(), payload.action())
                )
        );
        // Any member of the round roster is accepted, alive or dead (owner decision 2026-10-07 D7), so a refused pick
        // can never reveal a death. / 接受本局名单内任何成员，无论生死（所有者 2026-10-07 决定 D7），拒绝选择永远不会暴露死亡。
        ServerPlayNetworking.registerGlobalReceiver(SelectMagicianTargetC2SPacket.ID, (payload, context) ->
                context.server().execute(() -> {
                    var game = dev.doctor4t.wathe.cca.GameWorldComponent.KEY.get(context.player().getWorld());
                    var role = game.getRole(context.player());
                    var component = MagicianPlayerComponent.KEY.get(context.player());
                    var entry = payload.target() == null ? null : component.rosterEntry(payload.target());
                    if (entry != null && role != null
                            && dev.caecorthus.sparkwitch.SparkWitchRoles.MAGICIAN_ID.equals(role.identifier())
                            && game.isRunning()) {
                        component.setSelectedTarget(entry.uuid(), entry.name());
                    }
                })
        );
        ServerPlayNetworking.registerGlobalReceiver(FireDeathRayC2SPacket.ID,
                (payload, context) -> MurderousWitchDeathRayService.fire(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(FirePotionLauncherC2SPacket.ID,
                (payload, context) -> PotionLauncherFireService.fire(context.player(), payload));
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
        ServerPlayNetworking.registerGlobalReceiver(SwordfishStabC2SPayload.ID,
                (payload, context) -> SwordfishStabService.use(context.player(), payload.targetEntityId()));
        ServerPlayNetworking.registerGlobalReceiver(SelectBlackRavenDisguiseC2SPacket.ID,
                (payload, context) -> BlackRavenDisguiseService.requestSwitch(
                        context.player(), payload.session(), payload.target()));
        ServerPlayNetworking.registerGlobalReceiver(UseBlindAttuneC2SPayload.ID,
                (payload, context) -> BlindAttuneService.tryUse(context.player()));
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
        // USEC payload types register in their own role-owned class, like the Seeker; the owning services register the
        // receivers from SparkWitchEvents, which runs after this. / USEC 数据包类型与搜寻者一样在自有类中注册；
        // 接收器由各自的服务在之后运行的 SparkWitchEvents 中注册。
        UsecNetworking.register();
    }
}
