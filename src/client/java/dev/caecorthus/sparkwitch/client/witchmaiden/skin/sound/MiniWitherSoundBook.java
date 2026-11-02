package dev.caecorthus.sparkwitch.client.witchmaiden.skin.sound;

import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherSoundSlot;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * The bookkeeping behind {@link MiniWitherSoundOverrides}, free of Minecraft so it is unit-tested: which files each
 * slot plays, their private sound ids, and which ids failed. An id is a content key (skin, slot, normalized path, size
 * and modification time), so reloading an unchanged file reuses vanilla's cached decode, while a changed file or
 * another skin gets a new id and stale audio is never served. Nothing is ever evicted from vanilla's cache, so no
 * buffer a source may use is freed early.
 * {@link MiniWitherSoundOverrides} 背后的记账，不依赖 Minecraft，便于单元测试：每个槽位播放哪些文件、它们的私有声音
 * id，以及哪些 id 失败。id 是内容键（皮肤、槽位、规范化路径、大小与修改时间），因此重新加载未改动的文件会复用原版缓存
 * 的解码结果，而改动过的文件或其他皮肤会得到新 id，永远不会播放过期音频。从不从原版缓存淘汰任何条目，因此不会提前释放
 * 声源可能使用的缓冲。
 */
final class MiniWitherSoundBook {
    /** Private resource namespace of skin sounds; never on disk in a pack. / 皮肤声音的私有资源命名空间，不对应任何资源包。 */
    static final String NAMESPACE = "sparkwitch_skin";
    /** Skin folder contract: ogg files at most 8 MiB. / 皮肤文件夹契约：ogg 文件不超过 8 MiB。 */
    static final long MAX_FILE_BYTES = 8L * 1024L * 1024L;
    /** Skin folder contract: at most 32 ogg files in all. / 皮肤文件夹契约：ogg 文件合计不超过 32 个。 */
    static final int MAX_FILES = 32;
    /** Lead of every warning that drops a file. / 每条丢弃文件的警告的开头。 */
    static final String SKIPPED = "skipped (a slot with no file left plays vanilla): ";

    private final BiConsumer<Path, String> warn;
    /**
     * Ids whose decode failed. Their cached silence lives as long as vanilla's cache, so this outlives updates: an
     * unchanged broken file stays out, a changed one has a new id and is retried. Any thread.
     * 解码失败的 id。其缓存的静音与原版缓存同寿命，因此跨越更新保留：未改动的坏文件保持排除，改动后的文件有新 id 会重试。
     * 任意线程。
     */
    private final Set<String> failed = ConcurrentHashMap.newKeySet();
    /** Ids already warned about at decode time. / 解码时已警告过的 id。 */
    private final Set<String> warned = ConcurrentHashMap.newKeySet();
    private volatile Generation current = new Generation(Map.of());

    /**
     * {@code warn} receives each problem file and its reason: decode problems once per id, update-time rejections
     * once per update.
     * {@code warn} 接收每个出问题的文件及原因：解码问题每个 id 一次，更新时的拒绝每次更新一次。
     */
    MiniWitherSoundBook(BiConsumer<Path, String> warn) {
        this.warn = warn;
    }

    /** One playable file and its private sound id path. / 一个可播放文件及其私有声音 id 路径。 */
    record Entry(String soundPath, Path file) {
    }

    /** One immutable selection of files. / 一次不可变的文件选择。 */
    static final class Generation {
        private final Map<MiniWitherSoundSlot, List<Entry>> slots;
        private final Map<String, Path> files;

        private Generation(Map<MiniWitherSoundSlot, List<Entry>> slots) {
            this.slots = slots;
            Map<String, Path> byPath = new HashMap<>();
            slots.values().forEach(entries -> entries.forEach(entry -> byPath.put(entry.soundPath(), entry.file())));
            this.files = Map.copyOf(byPath);
        }

        boolean isEmpty() {
            return slots.isEmpty();
        }

        /** The file behind one of this selection's sound id paths. / 本次选择中某个声音 id 路径对应的文件。 */
        @Nullable
        Path file(String soundPath) {
            return files.get(soundPath);
        }
    }

    Generation current() {
        return current;
    }

    /**
     * The current slot files whose id has not failed; empty means the slot plays vanilla.
     * 当前槽位中 id 尚未失败的文件；为空表示该槽位播放原版。
     */
    List<Entry> playable(Generation generation, MiniWitherSoundSlot slot) {
        List<Entry> entries = generation.slots.getOrDefault(slot, List.of());
        if (failed.isEmpty()) {
            return entries;
        }
        List<Entry> left = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            if (!failed.contains(entry.soundPath())) {
                left.add(entry);
            }
        }
        return left;
    }

    /**
     * Drops the id {@code soundPath} from its slot for as long as the file is unchanged, and warns once. Any thread.
     * 只要文件未改动，就把 id {@code soundPath} 从其槽位移除，并只警告一次。任意线程。
     */
    void fail(String soundPath, Path file, String reason) {
        failed.add(soundPath);
        warnOnce(soundPath, file, reason);
    }

    /** Warns about the id {@code soundPath} once. Any thread. / 对 id {@code soundPath} 只警告一次。任意线程。 */
    void warnOnce(String soundPath, Path file, String reason) {
        if (warned.add(soundPath)) {
            warn.accept(file, reason);
        }
    }

    /**
     * Selects {@code files}: per slot in slot order, readable regular files of at most {@link #MAX_FILE_BYTES}, and
     * {@link #MAX_FILES} in all; a file listed twice in one slot counts once. Rejected files are warned once each.
     * 选择 {@code files}：按槽位顺序，每个槽位只收可读的普通文件且不超过 {@link #MAX_FILE_BYTES}，合计不超过
     * {@link #MAX_FILES} 个；同一槽位重复列出的文件只计一次。被拒绝的文件各警告一次。
     */
    synchronized Generation update(String skinKey, @Nullable Map<MiniWitherSoundSlot, List<Path>> files) {
        String skin = sanitize(skinKey);
        Map<MiniWitherSoundSlot, List<Entry>> slots = new EnumMap<>(MiniWitherSoundSlot.class);
        Map<Path, String> rejected = new LinkedHashMap<>();
        int kept = 0;
        if (files != null) {
            for (MiniWitherSoundSlot slot : MiniWitherSoundSlot.values()) {
                List<Path> candidates = files.get(slot);
                if (candidates == null) {
                    continue;
                }
                Map<String, Entry> entries = new LinkedHashMap<>();
                for (Path file : candidates) {
                    if (file == null) {
                        continue;
                    }
                    if (kept >= MAX_FILES) {
                        rejected.putIfAbsent(file, SKIPPED + "more than " + MAX_FILES + " sound files");
                        continue;
                    }
                    Probe probe = probe(file);
                    if (probe.problem() != null) {
                        rejected.putIfAbsent(file, SKIPPED + probe.problem());
                        continue;
                    }
                    String soundPath = skin + "/" + slot.key() + "_" + contentHash(skin, slot, file, probe.size(),
                            probe.modified());
                    if (entries.putIfAbsent(soundPath, new Entry(soundPath, file)) == null) {
                        kept++;
                    }
                }
                if (!entries.isEmpty()) {
                    slots.put(slot, List.copyOf(entries.values()));
                }
            }
        }
        Generation next = new Generation(Collections.unmodifiableMap(slots));
        rejected.forEach(warn);
        current = next;
        return next;
    }

    /** Every slot plays vanilla. / 所有槽位播放原版。 */
    synchronized Generation clear() {
        Generation next = new Generation(Map.of());
        current = next;
        return next;
    }

    /**
     * A stable 64-bit hex key of the skin, the slot, the normalized absolute path, the size and the modification time.
     * 皮肤、槽位、规范化绝对路径、大小与修改时间的稳定 64 位十六进制键。
     */
    static String contentHash(String skin, MiniWitherSoundSlot slot, Path file, long size, FileTime modified) {
        String key = skin + '\n' + slot.key() + '\n' + file.toAbsolutePath().normalize() + '\n' + size + '\n'
                + modified.toInstant();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is a required JDK algorithm", impossible);
        }
    }

    /**
     * Resource-path-safe form of a skin key: lower case, with anything outside {@code [a-z0-9._-]} replaced by
     * {@code _} (no {@code /}, so the id keeps its two parts).
     * 皮肤键的资源路径安全形式：小写，{@code [a-z0-9._-]} 以外的字符替换为 {@code _}（不含 {@code /}，id 保持两段）。
     */
    static String sanitize(@Nullable String skinKey) {
        if (skinKey == null || skinKey.isEmpty()) {
            return "skin";
        }
        StringBuilder safe = new StringBuilder(skinKey.length());
        for (char c : skinKey.toLowerCase(Locale.ROOT).toCharArray()) {
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
            safe.append(allowed ? c : '_');
        }
        return safe.toString();
    }

    /** Why {@code file} cannot be played, or null. / {@code file} 无法播放的原因，或 null。 */
    @Nullable
    static String check(Path file) {
        return probe(file).problem();
    }

    private record Probe(@Nullable String problem, long size, FileTime modified) {
    }

    private static Probe probe(Path file) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            if (!attributes.isRegularFile() || !Files.isReadable(file)) {
                return new Probe("not a readable file", 0L, FileTime.fromMillis(0L));
            }
            long size = attributes.size();
            String problem = size > MAX_FILE_BYTES ? "larger than 8 MiB (" + size + " bytes)" : null;
            return new Probe(problem, size, attributes.lastModifiedTime());
        } catch (IOException | SecurityException exception) {
            return new Probe("not a readable file (" + exception.getMessage() + ")", 0L, FileTime.fromMillis(0L));
        }
    }
}
