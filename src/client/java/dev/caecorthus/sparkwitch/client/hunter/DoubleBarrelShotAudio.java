package dev.caecorthus.sparkwitch.client.hunter;

import dev.caecorthus.sparkwitch.client.mixin.hunter.DoubleBarrelSourceAccessor;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterRules;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.Source;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.SOFTSourceSpatialize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Positional playback for the stereo double-barrel shot ({@code HunterRules.SHOT_SOUND_ID}). The file stays stereo;
 * OpenAL Soft's {@code AL_SOFT_source_spatialize} extension lets that one source use the linear clamped distance model
 * and world position vanilla already set on it, so the shot fades to silence at its 16-block sounds.json distance and
 * comes from the shooter's direction. Fails closed: without the extension, or on any LWJGL/AL failure, the shot keeps
 * today's non-positional stereo, and at most one log line is written. Never touches any other sound.
 * 立体声双管猎枪枪声（{@code HunterRules.SHOT_SOUND_ID}）的定位播放。音频文件保持立体声；OpenAL Soft 的
 * {@code AL_SOFT_source_spatialize} 扩展让该声源使用原版已设置的线性截断距离模型与世界坐标，因此枪声在 sounds.json 的
 * 16 格距离处衰减至无声，并从射手方向传来。失败即关闭：缺少该扩展或 LWJGL/AL 出错时，枪声保持现在的非定位立体声，
 * 且最多写一行日志。从不影响其他声音。
 */
public final class DoubleBarrelShotAudio {
    /** OpenAL Soft extension that lets a multi-channel source be spatialized. / 允许多声道声源参与空间化的 OpenAL Soft 扩展。 */
    static final String EXTENSION = "AL_SOFT_source_spatialize";
    private static final Logger LOGGER = LoggerFactory.getLogger("SparkWitch/DoubleBarrelShotAudio");
    private static final int UNKNOWN = 0;
    private static final int AVAILABLE = 1;
    private static final int UNAVAILABLE = 2;
    /**
     * Extension probe result, decided once on the sound executor (a single thread with the AL context current) and
     * never reset: support is a property of the OpenAL Soft build, not of the output device.
     * 扩展探测结果，在声音执行线程（单线程且 AL 上下文为当前）上判定一次且不重置：是否支持取决于 OpenAL Soft
     * 版本而非输出设备。
     */
    private static volatile int extensionState = UNKNOWN;

    private DoubleBarrelShotAudio() {
    }

    /** Whether this sound is the double-barrel shot; render thread. / 该声音是否为双管猎枪枪声；渲染线程。 */
    public static boolean shouldSpatialize(@Nullable SoundInstance sound) {
        return sound != null && isShotSound(sound.getId());
    }

    static boolean isShotSound(@Nullable Identifier soundId) {
        return HunterRules.SHOT_SOUND_ID.equals(soundId);
    }

    /**
     * Vanilla's source setup, plus spatialization for the shot only; any other sound gets {@code setup} itself.
     * 原版声源设置，仅对枪声额外开启空间化；其他声音原样返回 {@code setup}。
     */
    public static Consumer<Source> wrapSetup(@Nullable SoundInstance sound, Consumer<Source> setup) {
        if (!shouldSpatialize(sound)) {
            return setup;
        }
        return source -> {
            setup.accept(source);
            spatialize(source);
        };
    }

    /**
     * Turns on {@code AL_SOURCE_SPATIALIZE_SOFT} for {@code source}; sound executor thread only. An AL error from the
     * call leaves the property unset, which is today's behaviour, so the error flag is left for vanilla's own checks.
     * 为 {@code source} 开启 {@code AL_SOURCE_SPATIALIZE_SOFT}；仅限声音执行线程。该调用产生的 AL 错误会让属性保持
     * 未设置，即现在的行为，因此错误标志留给原版自己的检查。
     */
    public static void spatialize(Source source) {
        if (extensionState == UNAVAILABLE) {
            return;
        }
        try {
            if (extensionState == UNKNOWN) {
                boolean present = AL10.alIsExtensionPresent(EXTENSION);
                extensionState = present ? AVAILABLE : UNAVAILABLE;
                if (!present) {
                    LOGGER.info("OpenAL {} is missing; the double-barrel shot stays non-positional stereo", EXTENSION);
                    return;
                }
            }
            int pointer = ((DoubleBarrelSourceAccessor) source).sparkwitch$getDoubleBarrelSourcePointer();
            AL10.alSourcei(pointer, SOFTSourceSpatialize.AL_SOURCE_SPATIALIZE_SOFT, AL10.AL_TRUE);
        } catch (RuntimeException | LinkageError error) {
            extensionState = UNAVAILABLE;
            LOGGER.warn("Could not spatialize the double-barrel shot; it stays non-positional stereo", error);
        }
    }
}
