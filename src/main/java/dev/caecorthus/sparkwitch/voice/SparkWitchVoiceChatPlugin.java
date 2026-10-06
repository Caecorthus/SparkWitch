package dev.caecorthus.sparkwitch.voice;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithCommunicationPolicy;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment.hold.RecruitmentHold;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import dev.caecorthus.sparkwitch.roles.civilian.guardianangel.GuardianAngelRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Simple Voice Chat bridge for active Wraith outgoing silence, plus the Blind's lowest-priority voice perception.
 * Simple Voice Chat 桥接：阻止激活冤魂的外发语音，并以最低优先级提供盲人的语音感知。
 * On the physical client it also wires the Holy Flash incoming-voice muffle via {@link HolyFlashVoiceClientBridge}.
 * 在物理客户端上还会通过 {@link HolyFlashVoiceClientBridge} 接入圣光弹的传入语音压低。
 */
public final class SparkWitchVoiceChatPlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return SparkWitch.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(
                MicrophonePacketEvent.class,
                this::blockWraithSpeaker,
                Integer.MAX_VALUE
        );
        // Filter every server-to-client sound packet after Wathe's walkie relay has materialized it.
        // This covers native proximity/entity packets and TrainVoicePlugin's locational radio packets.
        registration.registerEvent(EntitySoundPacketEvent.class, this::blockRestrictedRecipient, Integer.MAX_VALUE);
        registration.registerEvent(LocationalSoundPacketEvent.class, this::blockRestrictedRecipient, Integer.MAX_VALUE);
        registration.registerEvent(StaticSoundPacketEvent.class, this::blockRestrictedRecipient, Integer.MAX_VALUE);
        // Lowest priority: the Blind only perceives speakers that no listener muted. / 最低优先级：盲人只感知未被静音的说话者。
        registration.registerEvent(
                MicrophonePacketEvent.class,
                BlindVoicePerceptionListener::onMicrophonePacket,
                Integer.MIN_VALUE
        );
        // Physical client only: Holy Flash muffles incoming voice; a no-op on dedicated servers.
        // 仅物理客户端：圣光弹压低传入语音；专用服务器上不做任何事。
        HolyFlashVoiceClientBridge.register(registration);
        VoicechatPlugin.super.registerEvents(registration);
    }

    private void blockRestrictedRecipient(de.maxhenkel.voicechat.api.events.PacketEvent<?> event) {
        if (SaboteurVoiceRules.shouldBlockPacket(event) || shouldBlockWraithRecipient(event)) {
            event.cancel();
        }
    }

    private boolean shouldBlockWraithRecipient(de.maxhenkel.voicechat.api.events.PacketEvent<?> event) {
        ServerPlayerEntity recipient = player(event.getReceiverConnection());
        if (recipient == null) {
            return false;
        }
        Role role = GameWorldComponent.KEY.get(recipient.getServerWorld()).getRole(recipient);
        return WraithCommunicationPolicy.shouldBlockCommunication(
                WraithStateService.isActive(recipient),
                GuardianAngelRules.isGuardianAngel(role),
                recipient.isCreative()
        );
    }

    private void blockWraithSpeaker(MicrophonePacketEvent event) {
        if (event.getSenderConnection() == null
                || event.getSenderConnection().getPlayer() == null
                || event.getSenderConnection().getPlayer().getPlayer() == null) {
            return;
        }
        ServerPlayerEntity speaker = (ServerPlayerEntity) event.getSenderConnection().getPlayer().getPlayer();
        if (KidnapperControlComponent.KEY.get(speaker).isControlled()
                && GameFunctions.isPlayerAliveAndSurvival(speaker)) {
            // 迷药控制期间目标黑屏且无法主动行动；语音也必须在同一入口静音，避免报点破坏劫持效果。
            event.cancel();
            return;
        }
        if (RiftSessionService.isInside(speaker)) {
            // Rift Gate occupants are muted but still hear (plan §6.6): no voice may leak from a gate, and the NR
            // Paranoid would otherwise hear them as non-swallowed spectators.
            // 裂隙门内的玩家被静音但仍能听见（plan §6.6）：门口不得传出人声，否则 NR 偏执杀手会把他们当作未被吞的旁观者听到。
            event.cancel();
            return;
        }
        if (RecruitmentHold.isHeld(speaker)) {
            // A held recruit is invisible inside the Grand Witch, and the stun lock leaves voice keys usable, so the
            // hold mutes them here; they still hear. The Blind voice listener skips cancelled packets.
            // 被定身的新共犯隐身站在大魔女体内，而眩晕锁保留语音按键，因此在此静音；仍能听见。盲人语音监听会跳过已取消的数据包。
            event.cancel();
            return;
        }
        Role role = GameWorldComponent.KEY.get(speaker.getServerWorld()).getRole(speaker);
        if (WraithCommunicationPolicy.shouldBlockCommunication(
                WraithStateService.isActive(speaker),
                GuardianAngelRules.isGuardianAngel(role),
                speaker.isCreative()
        )) {
            event.cancel();
        }
    }

    private ServerPlayerEntity player(de.maxhenkel.voicechat.api.VoicechatConnection connection) {
        if (connection == null || connection.getPlayer() == null
                || !(connection.getPlayer().getPlayer() instanceof ServerPlayerEntity player)) {
            return null;
        }
        return player;
    }
}
