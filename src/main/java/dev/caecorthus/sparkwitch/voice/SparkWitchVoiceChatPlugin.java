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
import dev.caecorthus.sparkwitch.roles.civilian.guardianangel.GuardianAngelRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Simple Voice Chat bridge for active Wraith outgoing silence only.
 * Simple Voice Chat 桥接仅阻止激活冤魂的外发语音。
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
