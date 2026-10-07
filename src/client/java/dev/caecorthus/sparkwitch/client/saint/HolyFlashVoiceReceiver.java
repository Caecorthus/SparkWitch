package dev.caecorthus.sparkwitch.client.saint;

import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;

/**
 * Client-only Simple Voice Chat receiver that muffles incoming voice on the flashed player's own client by the same
 * factor as game sounds. It scales decoded PCM before playback for proximity (entity), locational (radio) and static
 * (group) audio. Voice runs on SVC's audio thread, so it only reads the controller's volatile factor. Reached only
 * through {@code HolyFlashVoiceClientBridge} by reflection on the physical client.
 * 仅客户端的 Simple Voice Chat 接收器：在被闪玩家自己的客户端上按与游戏声音相同的系数压低传入语音。
 * 在播放前缩放解码后的 PCM，覆盖近距离（实体）、定位（对讲机）与静态（群组）语音。语音运行在 SVC 的音频线程，
 * 因此只读取控制器的 volatile 系数。只在物理客户端上经由 {@code HolyFlashVoiceClientBridge} 反射接入。
 */
public final class HolyFlashVoiceReceiver {
    private HolyFlashVoiceReceiver() {
    }

    public static void register(EventRegistration registration) {
        // SVC dispatches by exact event interface, so each subtype needs its own registration.
        // SVC 按确切的事件接口分发，因此每个子类型都需要单独注册。
        registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class, HolyFlashVoiceReceiver::muffle);
        registration.registerEvent(ClientReceiveSoundEvent.LocationalSound.class, HolyFlashVoiceReceiver::muffle);
        registration.registerEvent(ClientReceiveSoundEvent.StaticSound.class, HolyFlashVoiceReceiver::muffle);
    }

    private static void muffle(ClientReceiveSoundEvent event) {
        if (event.isCancelled()) {
            return;
        }
        float factor = HolyFlashAudioClient.voiceFactor();
        if (!(factor < 1.0F)) {
            return;
        }
        short[] pcm = event.getRawAudio();
        short[] scaled = HolyFlashAudioCurve.scalePcm(pcm, factor);
        if (scaled != pcm) {
            event.setRawAudio(scaled);
        }
    }
}
