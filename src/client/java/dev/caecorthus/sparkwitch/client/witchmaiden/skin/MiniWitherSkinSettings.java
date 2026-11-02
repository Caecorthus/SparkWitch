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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * The selected skin id, key {@value #KEY} in {@code config/sparkwitch-client.json}. The file is shared with
 * {@link ScopeSettingsStore}: a write re-reads the file and merges only this key, so the scope keys and any unknown key
 * survive. A missing, unreadable or non-string value means the default (vanilla); I/O errors are logged only.
 * 选中的皮肤 id，保存在 {@code config/sparkwitch-client.json} 的 {@value #KEY} 键。该文件与 {@link ScopeSettingsStore}
 * 共用：写入时重新读取文件并只合并本键，瞄准镜的键与任何未知键都会保留。值缺失、无法读取或不是字符串时视为默认（原版）；
 * I/O 错误只记录日志。
 */
final class MiniWitherSkinSettings {
    static final String KEY = "miniWitherSkin";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Test seam ({@link #useFile}); null means the config directory. / 测试接缝；null 表示配置目录。 */
    @Nullable
    private static Path file;

    private MiniWitherSkinSettings() {
    }

    /** The stored id, trimmed and lower-cased; {@code ""} for the default. / 已保存的 id（去空白、转小写）。 */
    static String read() {
        JsonObject json = readObject(path());
        if (json == null) {
            return MiniWitherSkinManager.DEFAULT_ID;
        }
        JsonElement value = json.get(KEY);
        if (value instanceof JsonPrimitive primitive && primitive.isString()) {
            return normalize(primitive.getAsString());
        }
        return MiniWitherSkinManager.DEFAULT_ID;
    }

    /** Merges {@code id} into the file; false when the write failed. / 把 id 合并写入文件；写入失败时返回 false。 */
    static boolean write(String id) {
        Path path = path();
        JsonObject json = readObject(path);
        if (json == null) {
            json = new JsonObject();
        }
        json.addProperty(KEY, normalize(id));
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, GSON.toJson(json), StandardCharsets.UTF_8);
            return true;
        } catch (IOException | RuntimeException exception) {
            SparkWitch.LOGGER.warn("Unable to save the Mini Wither skin setting to {}", path, exception);
            return false;
        }
    }

    static String normalize(@Nullable String id) {
        return id == null ? MiniWitherSkinManager.DEFAULT_ID : id.trim().toLowerCase(Locale.ROOT);
    }

    @Nullable
    private static JsonObject readObject(Path path) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (IOException | RuntimeException exception) {
            SparkWitch.LOGGER.warn("Unable to read the SparkWitch client settings {}", path, exception);
            return null;
        }
    }

    /** Test seam: reads and writes {@code testFile} instead (null: the config directory). / 测试接缝。 */
    static void useFile(@Nullable Path testFile) {
        file = testFile;
    }

    private static Path path() {
        Path override = file;
        return override != null ? override
                : FabricLoader.getInstance().getConfigDir().resolve(ScopeSettingsStore.FILE_NAME);
    }
}
