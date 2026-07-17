package dev.caecorthus.sparkwitch.voice;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
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
        VoicechatPlugin.super.registerEvents(registration);
    }

    private void blockWraithSpeaker(MicrophonePacketEvent event) {
        if (event.getSenderConnection() == null
                || event.getSenderConnection().getPlayer() == null
                || event.getSenderConnection().getPlayer().getPlayer() == null) {
            return;
        }
        ServerPlayerEntity speaker = (ServerPlayerEntity) event.getSenderConnection().getPlayer().getPlayer();
        if (WraithStateService.isActive(speaker)) {
            event.cancel();
        }
    }
}
