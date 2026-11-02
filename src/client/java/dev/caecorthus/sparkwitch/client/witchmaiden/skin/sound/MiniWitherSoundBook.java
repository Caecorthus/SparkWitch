package dev.caecorthus.sparkwitch.client.witchmaiden.skin.sound;

import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherSoundSlot;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * The bookkeeping behind {@link MiniWitherSoundOverrides}, free of Minecraft so it is unit-tested: which files each
 * slot plays, their private sound ids, and which files failed. Every {@link #update} or {@link #clear} starts a new
 * generation, and the generation is part of each sound id, so the vanilla static-sound cache never serves an earlier
 * skin's audio and nothing earlier has to be evicted from it.
 * {@link MiniWitherSoundOverrides} 背后的记账，不依赖 Minecraft，便于单元测试：每个槽位播放哪些文件、它们的私有声音
 * id，以及哪些文件失败。每次 {@link #update} 或 {@link #clear} 都开始新的一代，代号写在每个声音 id 中，因此原版静态
 * 声音缓存永远不会提供旧皮肤的音频，也无需从中淘汰任何旧条目。
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
    private long lastGeneration;
    private volatile Generation current = Generation.empty(0L);

    /**
     * {@code warn} receives each problem file and its reason, at most once per file per generation.
     * {@code warn} 接收每个出问题的文件及原因，每代每个文件最多一次。
     */
    MiniWitherSoundBook(BiConsumer<Path, String> warn) {
        this.warn = warn;
    }

    /** One playable file and its private sound id path. / 一个可播放文件及其私有声音 id 路径。 */
    record Entry(String soundPath, Path file) {
    }

    /**
     * One immutable selection of files plus its failure set. Decoding runs on a worker thread, so failures are a
     * concurrent set; everything else is fixed at creation.
     * 一次不可变的文件选择及其失败集合。解码在工作线程运行，因此失败集合是并发集合；其余内容在创建时即固定。
     */
    static final class Generation {
        private final long id;
        private final Map<MiniWitherSoundSlot, List<Entry>> slots;
        private final Map<String, Path> files;
        private final Set<Path> failed = ConcurrentHashMap.newKeySet();
        private final Set<Path> warned = ConcurrentHashMap.newKeySet();
        private final BiConsumer<Path, String> warn;

        private Generation(long id, Map<MiniWitherSoundSlot, List<Entry>> slots, BiConsumer<Path, String> warn) {
            this.id = id;
            this.slots = slots;
            this.warn = warn;
            Map<String, Path> byPath = new HashMap<>();
            slots.values().forEach(entries -> entries.forEach(entry -> byPath.put(entry.soundPath(), entry.file())));
            this.files = Map.copyOf(byPath);
        }

        private static Generation empty(long id) {
            return new Generation(id, Map.of(), (file, reason) -> {
            });
        }

        long id() {
            return id;
        }

        boolean isEmpty() {
            return slots.isEmpty();
        }

        /**
         * The slot's files that have not failed; empty means the slot plays vanilla.
         * 该槽位尚未失败的文件；为空表示该槽位播放原版。
         */
        List<Entry> playable(MiniWitherSoundSlot slot) {
            List<Entry> entries = slots.getOrDefault(slot, List.of());
            if (failed.isEmpty()) {
                return entries;
            }
            List<Entry> left = new ArrayList<>(entries.size());
            for (Entry entry : entries) {
                if (!failed.contains(entry.file())) {
                    left.add(entry);
                }
            }
            return left;
        }

        /** The file behind one of this generation's sound id paths. / 本代某个声音 id 路径对应的文件。 */
        @Nullable
        Path file(String soundPath) {
            return files.get(soundPath);
        }

        /**
         * Drops {@code file} from every slot of this generation and warns once. Any thread.
         * 把 {@code file} 从本代所有槽位移除并只警告一次。任意线程。
         */
        void fail(Path file, String reason) {
            failed.add(file);
            warnOnce(file, reason);
        }

        /** Warns about {@code file} once per generation. Any thread. / 每代对 {@code file} 只警告一次。任意线程。 */
        void warnOnce(Path file, String reason) {
            if (warned.add(file)) {
                warn.accept(file, reason);
            }
        }
    }

    Generation current() {
        return current;
    }

    /**
     * Starts a new generation playing {@code files}: per slot in slot order, readable regular files of at most
     * {@link #MAX_FILE_BYTES}, and {@link #MAX_FILES} in all. Rejected files are warned once and skipped.
     * 开始播放 {@code files} 的新一代：按槽位顺序，每个槽位只收可读的普通文件且不超过 {@link #MAX_FILE_BYTES}，合计不超过
     * {@link #MAX_FILES} 个。被拒绝的文件警告一次后跳过。
     */
    synchronized Generation update(String skinKey, @Nullable Map<MiniWitherSoundSlot, List<Path>> files) {
        long id = ++lastGeneration;
        String prefix = sanitize(skinKey) + "/" + id + "/";
        Map<MiniWitherSoundSlot, List<Entry>> slots = new EnumMap<>(MiniWitherSoundSlot.class);
        Map<Path, String> rejected = new HashMap<>();
        int kept = 0;
        if (files != null) {
            for (MiniWitherSoundSlot slot : MiniWitherSoundSlot.values()) {
                List<Path> candidates = files.get(slot);
                if (candidates == null) {
                    continue;
                }
                List<Entry> entries = new ArrayList<>();
                for (Path file : candidates) {
                    if (file == null) {
                        continue;
                    }
                    String problem = kept >= MAX_FILES ? "more than " + MAX_FILES + " sound files" : check(file);
                    if (problem != null) {
                        rejected.putIfAbsent(file, SKIPPED + problem);
                        continue;
                    }
                    entries.add(new Entry(prefix + slot.key() + "_" + entries.size(), file));
                    kept++;
                }
                if (!entries.isEmpty()) {
                    slots.put(slot, List.copyOf(entries));
                }
            }
        }
        Generation next = new Generation(id, Collections.unmodifiableMap(slots), warn);
        rejected.forEach(next::warnOnce);
        current = next;
        return next;
    }

    /** Starts a new, empty generation: every slot plays vanilla. / 开始新的空代：所有槽位播放原版。 */
    synchronized Generation clear() {
        Generation next = Generation.empty(++lastGeneration);
        current = next;
        return next;
    }

    /**
     * Resource-path-safe form of a skin key: lower case, with anything outside {@code [a-z0-9._-]} replaced by
     * {@code _} (no {@code /}, so the id keeps its three parts).
     * 皮肤键的资源路径安全形式：小写，{@code [a-z0-9._-]} 以外的字符替换为 {@code _}（不含 {@code /}，id 保持三段）。
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
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            return "not a readable file";
        }
        try {
            long size = Files.size(file);
            return size > MAX_FILE_BYTES ? "larger than 8 MiB (" + size + " bytes)" : null;
        } catch (IOException exception) {
            return "unreadable (" + exception.getMessage() + ")";
        }
    }
}
