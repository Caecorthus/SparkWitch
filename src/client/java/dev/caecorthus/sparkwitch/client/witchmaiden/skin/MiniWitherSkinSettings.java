package dev.caecorthus.sparkwitch.client.witchmaiden.skin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.scope.ScopeSettingsStore;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * The selected skin id, key {@value #KEY} in {@code config/sparkwitch-client.json}. The file is shared with
 * {@link ScopeSettingsStore}: a write re-reads the file and merges only this key, so the scope keys and any unknown key
 * survive. A missing file or a non-string value means the default (vanilla). A file that exists but cannot be read or
 * is not a JSON object is never written (that would wipe the scope keys): the choice stays live for this session and a
 * warning is logged once. Writes go to a temporary file in the same folder that then replaces the file atomically.
 * 选中的皮肤 id，保存在 {@code config/sparkwitch-client.json} 的 {@value #KEY} 键。该文件与 {@link ScopeSettingsStore}
 * 共用：写入时重新读取文件并只合并本键，瞄准镜的键与任何未知键都会保留。文件缺失或值不是字符串时视为默认（原版）。文件存在但
 * 无法读取或不是 JSON 对象时绝不写入（否则会抹掉瞄准镜的键）：选择只在本次会话中生效，并只记录一次警告。写入先写同目录的
 * 临时文件，再原子替换原文件。
 */
final class MiniWitherSkinSettings {
    static final String KEY = "miniWitherSkin";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Test seam ({@link #useFile}); null means the config directory. / 测试接缝；null 表示配置目录。 */
    @Nullable
    private static Path file;
    /** The unreadable-file warning was logged. / 已记录过文件无法读取的警告。 */
    private static boolean unreadableWarned;

    private MiniWitherSkinSettings() {
    }

    /** The file's content: absent, an object, or unreadable. / 文件内容：不存在、对象或无法读取。 */
    private record Content(@Nullable JsonObject json, boolean unreadable) {
        static final Content ABSENT = new Content(null, false);
        static final Content UNREADABLE = new Content(null, true);
    }

    /** The stored id, trimmed and lower-cased; {@code ""} for the default. / 已保存的 id（去空白、转小写）。 */
    static synchronized String read() {
        JsonObject json = readContent(path()).json();
        if (json == null) {
            return MiniWitherSkinManager.DEFAULT_ID;
        }
        JsonElement value = json.get(KEY);
        if (value instanceof JsonPrimitive primitive && primitive.isString()) {
            return normalize(primitive.getAsString());
        }
        return MiniWitherSkinManager.DEFAULT_ID;
    }

    /**
     * Merges {@code id} into the file; false when nothing was written (an unreadable file, or a failed write).
     * 把 id 合并写入文件；未写入时（文件无法读取或写入失败）返回 false。
     */
    static synchronized boolean write(String id) {
        Path path = path();
        Content content = readContent(path);
        if (content.unreadable()) {
            return false;
        }
        JsonObject json = content.json() != null ? content.json() : new JsonObject();
        json.addProperty(KEY, normalize(id));
        try {
            replaceAtomically(path, GSON.toJson(json));
            return true;
        } catch (IOException | RuntimeException exception) {
            SparkWitch.LOGGER.warn("Unable to save the Mini Wither skin setting to {}", path, exception);
            return false;
        }
    }

    static String normalize(@Nullable String id) {
        return id == null ? MiniWitherSkinManager.DEFAULT_ID : id.trim().toLowerCase(Locale.ROOT);
    }

    private static Content readContent(Path path) {
        if (!Files.exists(path)) {
            return Content.ABSENT;
        }
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            if (text.isBlank()) {
                // Nothing to lose: written as a fresh object. / 没有可丢失的内容：按新对象写入。
                return Content.ABSENT;
            }
            JsonElement element = JsonParser.parseString(text);
            if (element.isJsonObject()) {
                unreadableWarned = false;
                return new Content(element.getAsJsonObject(), false);
            }
            warnUnreadable(path, null);
        } catch (IOException | RuntimeException exception) {
            warnUnreadable(path, exception);
        }
        return Content.UNREADABLE;
    }

    private static void warnUnreadable(Path path, @Nullable Exception exception) {
        if (unreadableWarned) {
            return;
        }
        unreadableWarned = true;
        SparkWitch.LOGGER.warn("The SparkWitch client settings {} are unreadable or not a JSON object; the Mini Wither"
                + " skin choice applies for this session only and is not saved until the file is fixed", path,
                exception);
    }

    /**
     * Writes a temporary file next to {@code path}, then moves it over {@code path} atomically (a plain replace where
     * the file system has no atomic move), so a crash never leaves a half-written settings file.
     * 先在 {@code path} 旁写临时文件，再原子地移动覆盖 {@code path}（文件系统不支持原子移动时普通替换），崩溃时绝不会留下
     * 写了一半的设置文件。
     */
    private static void replaceAtomically(Path path, String text) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Test seam: reads and writes {@code testFile} instead (null: the config directory) and forgets the logged warning.
     * 测试接缝：改为读写 {@code testFile}（null：恢复配置目录），并清除已记录警告的状态。
     */
    static synchronized void useFile(@Nullable Path testFile) {
        file = testFile;
        unreadableWarned = false;
    }

    private static Path path() {
        Path override = file;
        return override != null ? override
                : FabricLoader.getInstance().getConfigDir().resolve(ScopeSettingsStore.FILE_NAME);
    }
}
