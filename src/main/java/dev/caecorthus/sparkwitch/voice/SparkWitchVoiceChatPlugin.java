package dev.caecorthus.sparkwitch.voice;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.caecorthus.sparkwitch.roles.civilian.guardianangel.GuardianAngelRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
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
        registration.registerEvent(EntitySoundPacketEvent.class, this::blockSaboteurRecipient, Integer.MAX_VALUE);
        registration.registerEvent(LocationalSoundPacketEvent.class, this::blockSaboteurRecipient, Integer.MAX_VALUE);
        registration.registerEvent(StaticSoundPacketEvent.class, this::blockSaboteurRecipient, Integer.MAX_VALUE);
        VoicechatPlugin.super.registerEvents(registration);
    }

    private void blockSaboteurRecipient(de.maxhenkel.voicechat.api.events.PacketEvent<?> event) {
        if (SaboteurVoiceRules.shouldBlockPacket(event)) {
            event.cancel();
        }
    }

    private void blockWraithSpeaker(MicrophonePacketEvent event) {
        if (event.getSenderConnection() == null
                || event.getSenderConnection().getPlayer() == null
                || event.getSenderConnection().getPlayer().getPlayer() == null) {
            return;
        }
        ServerPlayerEntity speaker = (ServerPlayerEntity) event.getSenderConnection().getPlayer().getPlayer();
        Role role = GameWorldComponent.KEY.get(speaker.getServerWorld()).getRole(speaker);
        if (GuardianAngelRules.shouldBlockWraithMicrophone(WraithStateService.isActive(speaker), role)) {
            event.cancel();
        }
    }
}
