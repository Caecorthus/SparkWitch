package dev.caecorthus.sparkwitch.voice;

import de.maxhenkel.voicechat.api.events.EventRegistration;
import dev.caecorthus.sparkwitch.SparkWitch;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Common-side seam that wires the client-only Holy Flash voice muffle into Simple Voice Chat. SVC loads every
 * {@code voicechat} entrypoint on both sides, so this class names the client receiver only as a string and resolves
 * it by reflection on the physical client; on a dedicated server it does nothing. Any failure fails closed (voice is
 * simply not muffled). Without SVC installed the plugin, and therefore this class, is never loaded.
 * 公共侧接缝：把仅客户端的圣光弹语音压低接入 Simple Voice Chat。SVC 会在两端加载所有 {@code voicechat}
 * 入口，因此本类只以字符串引用客户端接收器，并只在物理客户端上通过反射解析；专用服务器上不做任何事。
 * 任何失败都按失败关闭处理（只是不压低语音）。未安装 SVC 时插件及本类都不会被加载。
 */
public final class HolyFlashVoiceClientBridge {
    static final String CLIENT_RECEIVER = "dev.caecorthus.sparkwitch.client.saint.HolyFlashVoiceReceiver";

    private HolyFlashVoiceClientBridge() {
    }

    static void register(EventRegistration registration) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return;
        }
        try {
            Class<?> receiver = Class.forName(CLIENT_RECEIVER, true, HolyFlashVoiceClientBridge.class.getClassLoader());
            receiver.getMethod("register", EventRegistration.class).invoke(null, registration);
        } catch (ReflectiveOperationException | LinkageError exception) {
            SparkWitch.LOGGER.warn("Holy Flash voice muffle unavailable; voice stays at normal volume.", exception);
        }
    }
}
