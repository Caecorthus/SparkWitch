package dev.caecorthus.sparkwitch.client.witchmaiden.skin.sound;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

/**
 * Pure PCM helpers for skin sounds, free of Minecraft so they are unit-tested. OpenAL never positions a multi-channel
 * buffer, so a stereo file would play flat in both ears; averaging it to mono keeps the server's position and fade.
 * 皮肤声音的纯 PCM 工具，不依赖 Minecraft，便于单元测试。OpenAL 从不定位多声道缓冲，立体声文件会在双耳平铺播放；
 * 平均混成单声道后才保留服务端给出的位置与距离衰减。
 */
final class MiniWitherSoundPcm {
    private MiniWitherSoundPcm() {
    }

    /** Decoded audio and its format. / 解码后的音频及其格式。 */
    record Pcm(ByteBuffer data, AudioFormat format) {
    }

    /**
     * Averages interleaved signed 16-bit channels (in {@code data}'s own byte order) into one channel, written to a new
     * direct buffer in that order; an incomplete trailing frame is dropped. Mono, or any format OpenAL decodes other
     * than signed 16-bit, is returned unchanged (the same buffer and format).
     * 把交错的有符号 16 位多声道（按 {@code data} 自身字节序）平均成单声道，按同一字节序写入新的直接缓冲；末尾不完整的
     * 帧被丢弃。单声道或非有符号 16 位的格式原样返回（同一缓冲与格式）。
     */
    static Pcm toMono(ByteBuffer data, AudioFormat format) {
        int channels = format.getChannels();
        if (channels <= 1 || format.getSampleSizeInBits() != 16
                || !AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())) {
            return new Pcm(data, format);
        }
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

    /**
     * Bytes of decoded 16-bit PCM in {@code seconds} of {@code format}: the decode budget for one file.
     * {@code format} 下 {@code seconds} 秒解码后 16 位 PCM 的字节数：单个文件的解码预算。
     */
    static int byteBudget(AudioFormat format, int seconds) {
        long bytes = (long) Math.ceil(format.getSampleRate()) * Math.max(1, format.getChannels()) * Short.BYTES
                * seconds;
        return (int) Math.min(Integer.MAX_VALUE - 16_384L, Math.max(Short.BYTES, bytes));
    }
}
