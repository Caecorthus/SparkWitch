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
 * {@code SparkAssistConfigManager} precedent): loaded once from {@link ScopeClient#register()} (or by the first
 * {@code ScopeSettingsApi} call, whichever comes first), saved on every change from the Accessibility screen. The
 * {@code apply*} setters behind {@code ScopeSettingsApi} (SparkAssist's SparkDLC settings screen) change the same live
 * settings without writing; {@link #save()} writes them. A missing or unreadable file means defaults; a save merges
 * into the file's existing JSON object, so unknown keys survive; I/O errors are logged and never break the options
 * screen.
 * {@link ScopeSettings} 的纯客户端持久化，保存在 {@code config/sparkwitch-client.json}（参照 SparkAssist 的
 * {@code SparkAssistConfigManager}）：由 {@link ScopeClient#register()}（或第一次 {@code ScopeSettingsApi} 调用，以先到者为准）
 * 加载一次，辅助功能界面每次修改时保存。{@code ScopeSettingsApi}（SparkAssist 的 SparkDLC 设置界面）背后的 {@code apply*}
 * 方法修改同一份实时设置但不写文件，由 {@link #save()} 写入。文件缺失或无法读取时使用默认值；保存时合并进文件已有的 JSON
 * 对象，未知键得以保留；I/O 错误只记录日志，绝不影响设置界面。
 */
public final class ScopeSettingsStore {
    public static final String FILE_NAME = "sparkwitch-client.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile ScopeSettings current = ScopeSettings.DEFAULTS;
    private static boolean loaded;
    /** An {@code apply*} change not yet written. / 尚未写入的 {@code apply*} 修改。 */
    private static boolean unsaved;
    /** Test seam ({@link #useFile}); null means the config directory. / 测试接缝；null 表示配置目录。 */
    @Nullable
    private static Path file;

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

    /**
     * Live-only: the new mode takes effect at once and is written by the next {@link #save()}.
     * 仅实时生效：新模式立即生效，由下一次 {@link #save()} 写入。
     */
    public static void applyMode(ScopeMode mode) {
        apply(settings -> settings.withMode(mode));
    }

    /** Live-only, clamped and snapped like the file; see {@link #applyMode}. / 仅实时生效，钳制与取整同文件。 */
    public static void applySensitivityPercent(int percent) {
        apply(settings -> settings.withSensitivityPercent(percent));
    }

    /** Live-only; see {@link #applyMode}. / 仅实时生效。 */
    public static void applyLensResolutionPercent(int percent) {
        apply(settings -> settings.withLensResolutionPercent(percent));
    }

    /**
     * Writes the live settings when an {@code apply*} change is still unsaved; otherwise does nothing. A failed write
     * stays unsaved, so the next call retries.
     * 仍有未保存的 {@code apply*} 修改时写入实时设置，否则什么也不做。写入失败时保持未保存，下一次调用会重试。
     */
    public static synchronized void save() {
        if (unsaved) {
            write(current);
        }
    }

    private static synchronized void update(UnaryOperator<ScopeSettings> change) {
        ScopeSettings next = change.apply(current);
        if (next.equals(current)) {
            return;
        }
        current = next;
        write(next);
    }

    private static synchronized void apply(UnaryOperator<ScopeSettings> change) {
        ScopeSettings next = change.apply(current);
        if (next.equals(current)) {
            return;
        }
        current = next;
        unsaved = true;
    }

    /** Writes all three keys, so any pending {@code apply*} change goes with it. / 写入全部三个键，未保存的修改一并写入。 */
    private static void write(ScopeSettings settings) {
        Path path = path();
        JsonObject json = readObject(path);
        if (json == null) {
            json = new JsonObject();
        }
        settings.writeTo(json);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(json), StandardCharsets.UTF_8);
            unsaved = false;
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

    /**
     * Test seam: reads and writes {@code testFile} instead (null: the config directory again) and forgets the loaded
     * and unsaved state, so the next {@link #load()} starts afresh.
     * 测试接缝：改为读写 {@code testFile}（null：恢复配置目录），并清除已加载与未保存状态，下一次 {@link #load()} 重新开始。
     */
    static synchronized void useFile(@Nullable Path testFile) {
        file = testFile;
        loaded = false;
        unsaved = false;
        current = ScopeSettings.DEFAULTS;
    }

    private static Path path() {
        Path override = file;
        return override != null ? override : FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }
}
