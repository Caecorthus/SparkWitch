package dev.caecorthus.sparkwitch.voice;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithCommunicationRules;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Cancels active Wraith microphone packets while leaving incoming audio untouched.
 * 取消激活冤魂的麦克风数据包，同时不影响传入语音。
 */
public final class SparkWitchVoiceChatPlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return SparkWitch.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        // Run before Wathe can manually relay walkie-talkie audio.
        // 必须先于 Wathe 手动转发对讲机语音执行。
        registration.registerEvent(MicrophonePacketEvent.class, this::blockWraithSpeaker, Integer.MAX_VALUE);
        VoicechatPlugin.super.registerEvents(registration);
    }

    private void blockWraithSpeaker(MicrophonePacketEvent event) {
        if (event.getSenderConnection() == null
                || event.getSenderConnection().getPlayer() == null
                || event.getSenderConnection().getPlayer().getPlayer() == null) {
            return;
        }
        ServerPlayerEntity speaker = (ServerPlayerEntity) event.getSenderConnection().getPlayer().getPlayer();
        if (!WraithCommunicationRules.canSendVoice(WraithStateService.isActive(speaker))) {
            event.cancel();
        }
    }
}
