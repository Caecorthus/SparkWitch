package dev.caecorthus.sparkwitch.voice;

import de.maxhenkel.voicechat.api.events.EventRegistration;
import dev.caecorthus.sparkwitch.SparkWitch;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Common-side seam that wires the client-only BEAR hearing voice scaling (H7) into Simple Voice Chat, the same way as
 * {@code HolyFlashVoiceClientBridge}: SVC loads every {@code voicechat} entrypoint on both sides, so the client
 * receiver is named only as a string and resolved by reflection on the physical client; on a dedicated server this does
 * nothing. Any failure fails closed (voice keeps its normal volume). Without SVC the plugin, and this class, never load.
 * 公共侧接缝：与 {@code HolyFlashVoiceClientBridge} 相同，把仅客户端的 BEAR 听力语音缩放（H7）接入 Simple Voice Chat。SVC
 * 会在两端加载所有 {@code voicechat} 入口，因此客户端接收器只以字符串引用，并只在物理客户端上通过反射解析；专用服务器上不做
 * 任何事。任何失败都按失败关闭处理（语音保持正常音量）。未安装 SVC 时插件及本类都不会被加载。
 */
public final class BearHearingVoiceClientBridge {
    static final String CLIENT_RECEIVER = "dev.caecorthus.sparkwitch.client.bear.hearing.BearHearingVoiceReceiver";

    private BearHearingVoiceClientBridge() {
    }

    static void register(EventRegistration registration) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return;
        }
        try {
            Class<?> receiver = Class.forName(CLIENT_RECEIVER, true, BearHearingVoiceClientBridge.class.getClassLoader());
            receiver.getMethod("register", EventRegistration.class).invoke(null, registration);
        } catch (ReflectiveOperationException | LinkageError exception) {
            SparkWitch.LOGGER.warn("BEAR hearing voice scaling unavailable; voice stays at normal volume.", exception);
        }
    }
}
