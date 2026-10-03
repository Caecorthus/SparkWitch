package dev.caecorthus.sparkwitch.client.saint;

import dev.caecorthus.sparkwitch.roles.civilian.saint.flash.HolyFlashRules;

/**
 * Pure Holy Flash audio curves for the flashed player's own client: tinnitus ring volume and pitch, the muffle
 * factor applied to every other game sound and to proximity voice, and the re-volume cadence. All inputs are the
 * linear {@link HolyFlashRules#tinnitus} intensity (1 at the burst, 0 at the end).
 * 被闪玩家自己客户端上的圣光弹音频曲线（纯函数）：耳鸣音量与音调、作用于其他所有游戏声音和近距离语音的压音系数，
 * 以及重新设置音量的节奏。所有输入都是线性的 {@link HolyFlashRules#tinnitus} 强度（爆点时为 1，结束时为 0）。
 */
public final class HolyFlashAudioCurve {
    /** Loudest ring volume; the asset itself peaks near -9 dBFS. / 耳鸣最大音量；音频文件本身峰值约 -9 dBFS。 */
    public static final float RING_PEAK_VOLUME = 0.6F;
    /** Ring pitch at the very end; it holds 1.0 for the first half. / 结束时的耳鸣音调；前半段保持 1.0。 */
    public static final float RING_END_PITCH = 0.92F;
    /** Quietest share of normal volume other sounds keep at full intensity. / 最大强度时其他声音保留的最低音量比例。 */
    public static final float MUFFLE_FLOOR = 0.1F;
    public static final float MUFFLE_DEPTH = 1.0F - MUFFLE_FLOOR;
    /**
     * The muffle starts after the bright-spot ticks so the bang that arrives with the flash still plays at full
     * volume; already playing sounds are then pushed down on the next re-volume.
     * 压音在亮点阶段之后才开始，让与闪光同时到达的爆响仍以原音量播放；之后的重新设置音量会把正在播放的声音压低。
     */
    public static final int MUFFLE_ATTACK_TICKS = HolyFlashRules.SPOT_TICKS;
    /** Re-volume already playing static sources this often while muffled. / 压音期间每隔该刻数重新设置正在播放的静态声源音量。 */
    public static final int REAPPLY_INTERVAL_TICKS = 4;

    private HolyFlashAudioCurve() {
    }

    /**
     * Ring volume: an ease-out of the linear intensity so the ring stays loud early and fades late.
     * 耳鸣音量：对线性强度做缓出，使耳鸣前期保持响亮、后期才渐弱。
     */
    public static float ringVolume(float intensity) {
        float i = clamp01(intensity);
        return RING_PEAK_VOLUME * (1.0F - (1.0F - i) * (1.0F - i));
    }

    /**
     * Ring pitch: 1.0 for the first half of the flash, then a linear slide down to {@link #RING_END_PITCH}.
     * 耳鸣音调：闪光前半段为 1.0，之后线性降到 {@link #RING_END_PITCH}。
     */
    public static float ringPitch(float intensity) {
        float progress = 1.0F - clamp01(intensity);
        if (progress <= 0.5F) {
            return 1.0F;
        }
        return 1.0F - (1.0F - RING_END_PITCH) * ((progress - 0.5F) / 0.5F);
    }

    /**
     * Muffle intensity at {@code elapsedTicks} of {@code totalTicks}: 0 during the attack window, then the rules'
     * linear tinnitus intensity.
     * 在 {@code totalTicks} 中第 {@code elapsedTicks} 刻的压音强度：起始窗口内为 0，之后等于规则中的线性耳鸣强度。
     */
    public static float muffleIntensity(float elapsedTicks, int totalTicks) {
        if (totalTicks <= 0 || !(elapsedTicks >= MUFFLE_ATTACK_TICKS)) {
            return 0.0F;
        }
        return HolyFlashRules.tinnitus(elapsedTicks, totalTicks);
    }

    /**
     * Volume multiplier for every other sound: {@code max(0.1, 1 - 0.9 * intensity)}. Never at or below 0, because
     * vanilla {@code updateSoundVolume} stops a source whose recomputed volume is {@code <= 0}.
     * 其他所有声音的音量乘数：{@code max(0.1, 1 - 0.9 * intensity)}。永远不会小于等于 0，因为原版
     * {@code updateSoundVolume} 会停止重算音量 {@code <= 0} 的声源。
     */
    public static float muffleFactor(float intensity) {
        if (!(intensity > 0.0F)) {
            return 1.0F;
        }
        return Math.max(MUFFLE_FLOOR, 1.0F - MUFFLE_DEPTH * Math.min(1.0F, intensity));
    }

    /**
     * Whether to push a re-volume to playing sources this tick: at the muffle onset, every
     * {@link #REAPPLY_INTERVAL_TICKS} while muffled, and once more when it ends to restore full volume.
     * 本刻是否需要对正在播放的声源重新设置音量：压音开始时、压音期间每 {@link #REAPPLY_INTERVAL_TICKS} 刻一次，
     * 以及压音结束时再一次以恢复原音量。
     */
    public static boolean shouldReapply(boolean wasMuffling, boolean muffling, int ticksSinceReapply) {
        if (muffling) {
            return !wasMuffling || ticksSinceReapply >= REAPPLY_INTERVAL_TICKS;
        }
        return wasMuffling;
    }

    /**
     * Scales a 16-bit PCM voice frame by {@code factor}; returns the input untouched when no scaling applies
     * (factor at or above 1, empty frames, which mark the end of a transmission).
     * 按 {@code factor} 缩放 16 位 PCM 语音帧；无需缩放时（系数大于等于 1、或表示传输结束的空帧）原样返回输入。
     */
    public static short[] scalePcm(short[] pcm, float factor) {
        if (pcm == null || pcm.length == 0 || !(factor < 1.0F)) {
            return pcm;
        }
        float f = Math.max(0.0F, factor);
        short[] scaled = new short[pcm.length];
        for (int index = 0; index < pcm.length; index++) {
            int value = Math.round(pcm[index] * f);
            scaled[index] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
        }
        return scaled;
    }

    private static float clamp01(float value) {
        if (!(value > 0.0F)) {
            return 0.0F;
        }
        return Math.min(1.0F, value);
    }
}
