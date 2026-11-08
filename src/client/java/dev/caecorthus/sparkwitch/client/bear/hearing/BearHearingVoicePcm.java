package dev.caecorthus.sparkwitch.client.bear.hearing;

/**
 * Pure 16-bit PCM scaling for incoming Simple Voice Chat audio (H7). A factor below 1 attenuates linearly. A factor
 * above 1 (earplugs) boosts and then passes a soft limiter: samples stay linear up to {@link #KNEE} of full scale and
 * bend smoothly (tanh, slope 1 at the knee) towards full scale above it, so a loud voice is compressed instead of
 * hard-clipped at the 16-bit limit. Stateless and allocation-free when nothing changes; any thread.
 * 传入 Simple Voice Chat 音频的纯 16 位 PCM 缩放（H7）。倍数小于 1 时线性衰减。倍数大于 1（耳塞）时先放大再经过软限幅：
 * 样本在满幅的 {@link #KNEE} 以内保持线性，超过后平滑弯向满幅（tanh，拐点处斜率为 1），因此响亮的人声被压缩，而不是在 16 位
 * 上限处硬削波。无状态，不需要改变时不分配；任意线程可用。
 */
public final class BearHearingVoicePcm {
    /** Linear range of the limiter, as a share of full scale. / 限幅器线性区，占满幅的比例。 */
    public static final float KNEE = 0.75F;
    private static final float FULL_SCALE = 32767.0F;
    /** Output ceiling, one LSB under full scale, so even an extreme input never sits on the rail. / 输出上限。 */
    private static final float CEILING = 1.0F - 1.0F / FULL_SCALE;

    private BearHearingVoicePcm() {
    }

    /**
     * {@code pcm} scaled by {@code factor}; the input itself when the factor is 1 (or not a number) or the frame is
     * empty (an empty frame ends a transmission). / 按 {@code factor} 缩放的 {@code pcm}；倍数为 1（或非数字）或帧为空
     * （空帧表示传输结束）时原样返回输入。
     */
    public static short[] scale(short[] pcm, float factor) {
        if (pcm == null || pcm.length == 0 || !(factor >= 0.0F) || factor == 1.0F || Float.isInfinite(factor)) {
            return pcm;
        }
        short[] scaled = new short[pcm.length];
        if (factor < 1.0F) {
            for (int index = 0; index < pcm.length; index++) {
                scaled[index] = clampShort(Math.round(pcm[index] * factor));
            }
            return scaled;
        }
        for (int index = 0; index < pcm.length; index++) {
            scaled[index] = clampShort(Math.round(limit(pcm[index] * factor / FULL_SCALE) * FULL_SCALE));
        }
        return scaled;
    }

    /**
     * Soft limiter on a normalised sample: identity within ±{@link #KNEE}, then a tanh shoulder capped one LSB under
     * ±1. / 归一化样本的软限幅：±{@link #KNEE} 内恒等，之外为始终严格位于 ±1 内的 tanh 肩部。
     */
    static float limit(float sample) {
        float magnitude = Math.abs(sample);
        if (magnitude <= KNEE) {
            return sample;
        }
        float headroom = 1.0F - KNEE;
        float shaped = KNEE + headroom * (float) Math.tanh((magnitude - KNEE) / headroom);
        return Math.copySign(Math.min(shaped, CEILING), sample);
    }

    private static short clampShort(int value) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }
}
