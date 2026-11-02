package dev.caecorthus.sparkwitch.client.witchmaiden.skin.sound;

import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherSoundSlot;
import net.minecraft.client.sound.ChannelList;
import net.minecraft.client.sound.OggAudioStream;
import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.StaticSound;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.floatprovider.FloatSupplier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The selected skin's replacement sounds, swapped in for {@code sparkwitch:mini_wither.*} on this client only. The
 * server's volume, pitch, position and timing are kept; custom files are downmixed to mono so they stay positional.
 * 选中皮肤的替换声音，只在本客户端替换 {@code sparkwitch:mini_wither.*}。保留服务端给出的音量、音调、位置与时机；
 * 自定义文件会混成单声道，以保持方位感。
 *
 * <p>Client-only presentation: the server still plays its own event id with its own category, volume and pitch;
 * {@code mixin/witchmaiden/MiniWitherSkinSoundManagerMixin} swaps the sound set this client resolves for that id, and
 * {@code MiniWitherSkinSoundLoaderMixin} decodes the private {@value MiniWitherSoundBook#NAMESPACE} ids from disk into
 * vanilla's static-sound cache, which closes them with the sound engine (never earlier, so no playing source loses its
 * buffer). An unreadable or corrupt file is warned once and leaves its slot; a slot with no file left plays vanilla.
 * 仅客户端表现：服务端仍以自己的类别、音量与音调播放自有事件 id；{@code MiniWitherSkinSoundManagerMixin} 替换本客户端
 * 为该 id 解析出的声音集合，{@code MiniWitherSkinSoundLoaderMixin} 把私有 {@value MiniWitherSoundBook#NAMESPACE} id
 * 从磁盘解码进原版静态声音缓存，由声音引擎关闭时一并释放（绝不提前，因此正在播放的声源不会失去缓冲）。无法读取或损坏的
 * 文件只警告一次并退出其槽位；没有剩余文件的槽位播放原版。
 */
public final class MiniWitherSoundOverrides {
    private static final Logger LOGGER = LoggerFactory.getLogger("SparkWitch/MiniWitherSoundOverrides");
    /** Vanilla's sounds.json defaults (volume, pitch, weight 1; 16-block attenuation). / 原版 sounds.json 默认值。 */
    private static final FloatSupplier UNIT = random -> 1.0F;
    private static final int ATTENUATION = 16;
    /**
     * Decoded length cap per file: a long track dropped in by mistake must not fill memory; the rest is cut.
     * 每个文件的解码时长上限：误放的长音轨不能占满内存，超出部分被截断。
     */
    static final int MAX_SECONDS = 60;
    private static final int CHUNK_BYTES = 16_384;
    /** {@link Sound#FINDER}'s directory and extension. / {@link Sound#FINDER} 的目录与扩展名。 */
    private static final String SOUNDS_PREFIX = "sounds/";
    private static final String OGG_SUFFIX = ".ogg";
    private static final MiniWitherSoundBook BOOK = new MiniWitherSoundBook(
            (file, reason) -> LOGGER.warn("Mini Wither skin sound {}: {}", file, reason));

    private MiniWitherSoundOverrides() {
    }

    /**
     * Replaces every override; slots missing from {@code files} play vanilla. {@code skinKey} is a lower-case
     * identifier-safe tag of the skin. Client thread.
     * 替换全部覆盖项；{@code files} 中缺少的槽位播放原版。{@code skinKey} 为皮肤的小写、可用于标识符的标记。客户端线程。
     */
    public static void update(String skinKey, Map<MiniWitherSoundSlot, List<Path>> files) {
        BOOK.update(skinKey, files);
    }

    public static void clear() {
        BOOK.clear();
    }

    /**
     * Mixin seam for {@code SoundManager#get}: the sound set to play for {@code eventId}. For a Mini Wither event whose
     * slot has files this is a fresh set of those files (equal weight, not streamed) with vanilla's subtitle; anything
     * else, or a missing vanilla set, returns {@code vanilla}. Client thread.
     * {@code SoundManager#get} 的 mixin 接缝：为 {@code eventId} 播放的声音集合。对槽位有文件的小凋灵事件，返回由这些文件
     * 组成的新集合（等权重、非流式），字幕与原版相同；其他情况或原版集合缺失时返回 {@code vanilla}。客户端线程。
     */
    @Nullable
    public static WeightedSoundSet soundSet(Identifier eventId, @Nullable WeightedSoundSet vanilla) {
        MiniWitherSoundBook.Generation generation = BOOK.current();
        if (vanilla == null || generation.isEmpty()) {
            return vanilla;
        }
        MiniWitherSoundSlot slot = MiniWitherSoundSlot.byEventId(eventId);
        if (slot == null) {
            return vanilla;
        }
        List<MiniWitherSoundBook.Entry> entries = generation.playable(slot);
        if (entries.isEmpty()) {
            return vanilla;
        }
        WeightedSoundSet set = new WeightedSoundSet(eventId, subtitleKey(vanilla));
        for (MiniWitherSoundBook.Entry entry : entries) {
            set.add(new Sound(Identifier.of(MiniWitherSoundBook.NAMESPACE, entry.soundPath()), UNIT, UNIT, 1,
                    Sound.RegistrationType.FILE, false, false, ATTENUATION));
        }
        return set;
    }

    /** Whether {@code location} is a skin sound file id. / {@code location} 是否为皮肤声音文件 id。 */
    public static boolean serves(Identifier location) {
        return MiniWitherSoundBook.NAMESPACE.equals(location.getNamespace());
    }

    /**
     * Mixin seam for {@code SoundLoader#loadStatic}, called on a cache miss: resolves the file against the current
     * generation now (same client-thread call as {@link #soundSet}) and decodes it on vanilla's sound worker. A failure
     * resolves to a short silence instead of failing, because vanilla never reaps a source whose buffer never came.
     * {@code SoundLoader#loadStatic} 的 mixin 接缝，在缓存未命中时调用：立即按当前代解析文件（与 {@link #soundSet} 处于
     * 同一次客户端线程调用），并在原版声音工作线程解码。失败时给出一小段静音而不是失败，因为原版永远不会回收一个始终
     * 没拿到缓冲的声源。
     */
    public static CompletableFuture<StaticSound> loadAsync(Identifier location) {
        MiniWitherSoundBook.Generation generation = BOOK.current();
        String path = location.getPath();
        Path file = path.startsWith(SOUNDS_PREFIX) && path.endsWith(OGG_SUFFIX)
                ? generation.file(Sound.FINDER.toResourceId(location).getPath()) : null;
        if (file == null) {
            return CompletableFuture.completedFuture(silence());
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return decode(generation, file);
            } catch (IOException | RuntimeException exception) {
                generation.fail(file, MiniWitherSoundBook.SKIPPED + "cannot be decoded as Ogg Vorbis (" + exception
                        + ")");
                return silence();
            }
        }, Util.getDownloadWorkerExecutor());
    }

    /**
     * Vanilla's static decode ({@link OggAudioStream} into a {@link ChannelList}), bounded to {@link #MAX_SECONDS},
     * then a mono downmix.
     * 原版的静态解码（{@link OggAudioStream} 写入 {@link ChannelList}），上限 {@link #MAX_SECONDS} 秒，然后混成单声道。
     */
    private static StaticSound decode(MiniWitherSoundBook.Generation generation, Path file) throws IOException {
        String problem = MiniWitherSoundBook.check(file);
        if (problem != null) {
            throw new IOException(problem);
        }
        try (InputStream input = new BufferedInputStream(Files.newInputStream(file));
             OggAudioStream ogg = new OggAudioStream(input)) {
            AudioFormat format = ogg.getFormat();
            int budget = MiniWitherSoundPcm.byteBudget(format, MAX_SECONDS);
            ChannelList pcm = new ChannelList(CHUNK_BYTES);
            boolean more;
            do {
                more = ogg.read(pcm);
            } while (more && pcm.getCurrentBufferSize() < budget);
            if (more) {
                generation.warnOnce(file, "longer than " + MAX_SECONDS + " s, only the first " + MAX_SECONDS
                        + " s play");
            }
            ByteBuffer data = pcm.getBuffer();
            if (!data.hasRemaining()) {
                throw new IOException("no audio");
            }
            MiniWitherSoundPcm.Pcm mono = MiniWitherSoundPcm.toMono(data, format);
            return new StaticSound(mono.data(), mono.format());
        }
    }

    /** A fresh 64-sample silence, so the source plays, stops and is reaped. / 新的 64 采样静音，声源播放后停止并被回收。 */
    private static StaticSound silence() {
        ByteBuffer zeros = ByteBuffer.allocateDirect(64 * Short.BYTES).order(ByteOrder.nativeOrder());
        return new StaticSound(zeros, new AudioFormat(44_100.0F, 16, 1, true, false));
    }

    @Nullable
    private static String subtitleKey(WeightedSoundSet vanilla) {
        Text subtitle = vanilla.getSubtitle();
        return subtitle != null && subtitle.getContent() instanceof TranslatableTextContent translatable
                ? translatable.getKey() : null;
    }
}
