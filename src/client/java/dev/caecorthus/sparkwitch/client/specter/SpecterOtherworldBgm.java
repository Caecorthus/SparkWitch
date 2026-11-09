package dev.caecorthus.sparkwitch.client.specter;

import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterOtherworldComponent;
import dev.doctor4t.ratatouille.client.util.ambience.AmbienceUtil;
import dev.doctor4t.ratatouille.client.util.ambience.BackgroundAmbience;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Otherworld BGM through the shared background-ambience system: the server picks one of the three tracks when the
 * window opens and syncs the variant, so every client plays the same song. The ambience loop is non-positional,
 * repeats for the whole window and fades out when the window closes.
 * 里世界 BGM 走现有的背景氛围系统：窗口开启时服务端从三首中选一并同步变体，所有客户端播放同一首。
 * 氛围循环是非位置音、整窗口循环，窗口关闭时淡出。
 */
public final class SpecterOtherworldBgm {
    /** Ambience fade-in/out in ticks. / 氛围淡入淡出刻数。 */
    private static final int FADE_TICKS = 10;
    private static boolean registered;

    private SpecterOtherworldBgm() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        for (int variant = 0; variant < SparkWitchSounds.SPECTER_OTHERWORLD_BGM.length; variant++) {
            final int index = variant;
            AmbienceUtil.registerBackgroundAmbience(new BackgroundAmbience(
                    SparkWitchSounds.SPECTER_OTHERWORLD_BGM[index],
                    player -> shouldPlay(player, index),
                    FADE_TICKS
            ));
        }
    }

    private static boolean shouldPlay(ClientPlayerEntity player, int variant) {
        if (player == null || player.getWorld() == null) {
            return false;
        }
        SpecterOtherworldComponent otherworld = SpecterOtherworldComponent.KEY.get(player.getWorld());
        return GameWorldComponent.KEY.get(player.getWorld()).isRunning()
                && otherworld.isActive()
                && otherworld.getBgmVariant() == variant;
    }
}
