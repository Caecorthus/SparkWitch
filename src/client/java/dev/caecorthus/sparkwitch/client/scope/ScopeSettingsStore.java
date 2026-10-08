package dev.caecorthus.sparkwitch.client.scope;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.caecorthus.sparkwitch.SparkWitch;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.UnaryOperator;

/**
 * Client-only persistence of {@link ScopeSettings} in {@code config/sparkwitch-client.json} (SparkAssist
 * {@code SparkAssistConfigManager} precedent): loaded once from {@link ScopeClient#register()}, saved on every change
 * from the Accessibility screen. A missing or unreadable file means defaults; a save merges into the file's existing
 * JSON object, so unknown keys survive; I/O errors are logged and never break the options screen.
 * {@link ScopeSettings} 的纯客户端持久化，保存在 {@code config/sparkwitch-client.json}（参照 SparkAssist 的
 * {@code SparkAssistConfigManager}）：由 {@link ScopeClient#register()} 加载一次，辅助功能界面每次修改时保存。文件缺失或无法
 * 读取时使用默认值；保存时合并进文件已有的 JSON 对象，未知键得以保留；I/O 错误只记录日志，绝不影响设置界面。
 */
public final class ScopeSettingsStore {
    public static final String FILE_NAME = "sparkwitch-client.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile ScopeSettings current = ScopeSettings.DEFAULTS;
    private static boolean loaded;

    private ScopeSettingsStore() {
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        current = ScopeSettings.fromJson(readObject(path()));
    }

    public static ScopeSettings current() {
        return current;
    }

    public static void setMode(ScopeMode mode) {
        update(settings -> settings.withMode(mode));
    }

    public static void setSensitivityPercent(int percent) {
        update(settings -> settings.withSensitivityPercent(percent));
    }

    public static void setLensResolutionPercent(int percent) {
        update(settings -> settings.withLensResolutionPercent(percent));
    }

    private static synchronized void update(UnaryOperator<ScopeSettings> change) {
        ScopeSettings next = change.apply(current);
        if (next.equals(current)) {
            return;
        }
        current = next;
        save(next);
    }

    private static void save(ScopeSettings settings) {
        Path path = path();
        JsonObject json = readObject(path);
        if (json == null) {
            json = new JsonObject();
        }
        settings.writeTo(json);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            SparkWitch.LOGGER.warn("Unable to save the SparkWitch client settings to {}", path, exception);
        }
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
            SparkWitch.LOGGER.warn("Unable to read the SparkWitch client settings {}; using defaults", path, exception);
            return null;
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }
}
