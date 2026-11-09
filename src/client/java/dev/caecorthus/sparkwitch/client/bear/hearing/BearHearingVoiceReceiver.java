package dev.caecorthus.sparkwitch.client.bear.hearing;

import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;

/**
 * Client-only Simple Voice Chat receiver for BEAR hearing (H7): incoming voice follows the same factor as game sounds
 * on the wearer's own client. It rescales decoded PCM before playback for proximity (entity), locational (radio) and
 * static (group) audio; below 1 it attenuates, above 1 (earplugs) it boosts through {@link BearHearingVoicePcm}'s soft
 * limiter. Registered after the Holy Flash receiver, so a flash muffle and the hearing factor multiply. Runs on SVC's
 * audio thread and only reads {@link BearHearingClient#voiceFactor()}. Reached only through
 * {@code voice.BearHearingVoiceClientBridge} by reflection on the physical client.
 * 仅客户端的 BEAR 听力 Simple Voice Chat 接收器（H7）：佩戴者自己的客户端上传入语音与游戏声音使用同一倍数。在播放前重新缩放
 * 解码后的 PCM，覆盖近距离（实体）、定位（对讲机）与静态（群组）语音；小于 1 时衰减，大于 1（耳塞）时经 {@link BearHearingVoicePcm}
 * 的软限幅放大。注册在圣光弹接收器之后，因此闪光压音与听力倍数相乘。运行在 SVC 的音频线程，只读取
 * {@link BearHearingClient#voiceFactor()}。只在物理客户端上经由 {@code voice.BearHearingVoiceClientBridge} 反射接入。
 */
public final class BearHearingVoiceReceiver {
    private BearHearingVoiceReceiver() {
    }

    public static void register(EventRegistration registration) {
        // SVC dispatches by exact event interface, so each subtype needs its own registration.
        // SVC 按确切的事件接口分发，因此每个子类型都需要单独注册。
        registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class, BearHearingVoiceReceiver::scale);
        registration.registerEvent(ClientReceiveSoundEvent.LocationalSound.class, BearHearingVoiceReceiver::scale);
        registration.registerEvent(ClientReceiveSoundEvent.StaticSound.class, BearHearingVoiceReceiver::scale);
    }

    private static void scale(ClientReceiveSoundEvent event) {
        if (event.isCancelled()) {
            return;
        }
        float factor = BearHearingClient.voiceFactor();
        if (factor == 1.0F) {
            return;
        }
        short[] pcm = event.getRawAudio();
        short[] scaled = BearHearingVoicePcm.scale(pcm, factor);
        if (scaled != pcm) {
            event.setRawAudio(scaled);
        }
    }
}
