package dev.caecorthus.sparkwitch.client.witchmaiden.skin.sound;

import org.jetbrains.annotations.Nullable;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

/**
 * Pure PCM helpers for skin sounds, free of Minecraft so they are unit-tested. OpenAL never positions a multi-channel
 * buffer, so a stereo file would play flat in both ears; averaging it to mono keeps the server's position and fade.
 * 皮肤声音的纯 PCM 工具，不依赖 Minecraft，便于单元测试。OpenAL 从不定位多声道缓冲，立体声文件会在双耳平铺播放；
 * 平均混成单声道后才保留服务端给出的位置与距离衰减。
 */
final class MiniWitherSoundPcm {
    /** Lowest accepted sample rate. / 可接受的最低采样率。 */
    static final int MIN_RATE = 8_000;
    /**
     * Highest accepted sample rate: 192 kHz files exist in the wild (our own shotgun shot is one); the fixed byte cap
     * below still bounds the decode.
     * 可接受的最高采样率：实际存在 192 kHz 的文件（我们自己的猎枪枪声就是）；下方的固定字节上限仍限制解码量。
     */
    static final int MAX_RATE = 192_000;
    /**
     * Fixed decode cap per file, independent of the file's own header: 60 s of 48 kHz 16-bit stereo (11,520,000
     * bytes). A header can claim anything (JOrbis accepts 1-255 channels and any rate), so it never sizes memory.
     * 每个文件固定的解码上限，与文件头无关：48 kHz 16 位立体声 60 秒（11,520,000 字节）。文件头可以声称任意值（JOrbis
     * 接受 1-255 个声道与任意采样率），因此绝不用它来决定内存大小。
     */
    static final int MAX_DECODED_BYTES = 60 * 48_000 * 2 * Short.BYTES;

    private MiniWitherSoundPcm() {
    }

    /** Decoded audio and its format. / 解码后的音频及其格式。 */
    record Pcm(ByteBuffer data, AudioFormat format) {
    }

    /**
     * Why a decoded format is refused before any sample is decoded, or null: only mono or stereo signed 16-bit PCM at
     * {@link #MIN_RATE}-{@link #MAX_RATE} Hz is played.
     * 在解码任何采样之前拒绝某格式的原因，或 null：只播放 {@link #MIN_RATE}-{@link #MAX_RATE} Hz 的单声道或立体声
     * 有符号 16 位 PCM。
     */
    @Nullable
    static String formatProblem(AudioFormat format) {
        int channels = format.getChannels();
        if (channels < 1 || channels > 2) {
            return channels + " channels (only mono or stereo)";
        }
        float rate = format.getSampleRate();
        if (!(rate >= MIN_RATE && rate <= MAX_RATE)) {
            return "sample rate " + rate + " Hz (only " + MIN_RATE + "-" + MAX_RATE + " Hz)";
        }
        if (format.getSampleSizeInBits() != 16 || !AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())) {
            return "not signed 16-bit PCM";
        }
        return null;
    }

    /**
     * Averages interleaved signed 16-bit channels (in {@code data}'s own byte order) into one channel, written to a new
     * direct buffer in that order; an incomplete trailing frame is dropped. Mono, or any format other than signed
     * 16-bit, is returned unchanged (the same buffer and format).
     * 把交错的有符号 16 位多声道（按 {@code data} 自身字节序）平均成单声道，按同一字节序写入新的直接缓冲；末尾不完整的
     * 帧被丢弃。单声道或非有符号 16 位的格式原样返回（同一缓冲与格式）。
     */
    static Pcm toMono(ByteBuffer data, AudioFormat format) {
        int channels = format.getChannels();
        if (channels <= 1 || format.getSampleSizeInBits() != 16
                || !AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())) {
            return new Pcm(data, format);
        }
        // frames * frameBytes <= remaining, so no int product here can overflow. / 各乘积不超过 remaining，不会溢出。
        int frameBytes = channels * Short.BYTES;
        int frames = data.remaining() / frameBytes;
        ByteBuffer mono = ByteBuffer.allocateDirect(frames * Short.BYTES).order(data.order());
        int start = data.position();
        for (int frame = 0; frame < frames; frame++) {
            int offset = start + frame * frameBytes;
            int sum = 0;
            for (int channel = 0; channel < channels; channel++) {
                sum += data.getShort(offset + channel * Short.BYTES);
            }
            // An average never leaves the 16-bit range. / 平均值不会超出 16 位范围。
            mono.putShort((short) (sum / channels));
        }
        mono.flip();
        return new Pcm(mono, new AudioFormat(format.getSampleRate(), 16, 1, true, format.isBigEndian()));
    }
}
