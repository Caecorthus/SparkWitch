package dev.caecorthus.sparkwitch.client.witchmaiden.skin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.bedrock.geo.BedrockGeometryParser;
import dev.caecorthus.sparkwitch.client.witchmaiden.skin.MiniWitherSkinFolder.InvalidSkinException;
import dev.caecorthus.sparkwitch.client.witchmaiden.skin.sound.MiniWitherSoundOverrides;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the Mini Wither skins: the skins directory ({@code config/sparkwitch/mini_wither/<id>/}), the selected id
 * (key {@code miniWitherSkin} in {@code config/sparkwitch-client.json}, merged like {@code ScopeSettingsStore}),
 * loading the selected folder, registering its textures and pushing its sounds to {@code MiniWitherSoundOverrides}.
 * Client thread only.
 * 管理小凋灵皮肤：皮肤目录（{@code config/sparkwitch/mini_wither/<id>/}）、选中的 id（保存在
 * {@code config/sparkwitch-client.json} 的 {@code miniWitherSkin} 键，与 {@code ScopeSettingsStore} 一样合并写入）、
 * 加载选中的文件夹、注册其贴图并把声音交给 {@code MiniWitherSoundOverrides}。仅限客户端线程。
 *
 * <p>Lifecycle: {@link #register()} only reads the setting; the first {@link #active()}, {@link #entries()} or
 * {@link #status()} on the render thread scans the folder and loads the selected skin; {@link #select} and
 * {@link #reload()} load synchronously. Every load first frees the previous skin's textures and sounds. No public
 * method throws: a failure is logged, shown through {@link #status()} and drawn as vanilla.
 * 生命周期：{@link #register()} 只读取设置；渲染线程上第一次调用 {@link #active()}、{@link #entries()} 或
 * {@link #status()} 时扫描文件夹并加载选中的皮肤；{@link #select} 与 {@link #reload()} 同步加载。每次加载前先释放上一个皮肤的
 * 贴图与声音。公共方法从不抛出异常：失败只记录日志、通过 {@link #status()} 显示，并按原版绘制。
 */
public final class MiniWitherSkinManager {
    /** The vanilla Wither (no skin). / 原版凋灵（无皮肤）。 */
    public static final String DEFAULT_ID = "";

    private static final Status DEFAULT_STATUS = new Status(Status.Kind.DEFAULT, "");

    private static boolean settingRead;
    /** The first scan and load ran. / 首次扫描与加载已执行。 */
    private static boolean initialized;
    private static String selected = DEFAULT_ID;
    private static List<Entry> entries = List.of();
    private static Map<String, Path> folders = Map.of();
    @Nullable
    private static volatile MiniWitherSkin active;
    private static volatile Status status = DEFAULT_STATUS;
    private static List<String> warnings = List.of();
    /** Textures the loaded (or broken) skin owns until the next load. / 已加载（或损坏）皮肤持有到下次加载的贴图。 */
    private static List<Identifier> textures = List.of();
    /** Bumped per load, so texture ids are never reused. / 每次加载递增，贴图 id 永不复用。 */
    private static int generation;

    private MiniWitherSkinManager() {
    }

    /** Client init: reads the setting; the first {@link #active()} call scans and loads. / 客户端初始化。 */
    public static void register() {
        try {
            readSetting();
        } catch (RuntimeException exception) {
            SparkWitch.LOGGER.warn("Unable to read the Mini Wither skin setting", exception);
        }
    }

    public static Path skinsDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve(SparkWitch.MOD_ID).resolve("mini_wither");
    }

    /** Skin folders found by the last scan, sorted by id; never includes the default. / 上次扫描找到的皮肤文件夹。 */
    public static List<Entry> entries() {
        ensureInitialized();
        return entries;
    }

    public static String selectedId() {
        try {
            readSetting();
        } catch (RuntimeException exception) {
            SparkWitch.LOGGER.warn("Unable to read the Mini Wither skin setting", exception);
        }
        return selected;
    }

    /** Selects, loads at once and saves; an unknown id or a failed load keeps the id and shows the error. */
    public static void select(String id) {
        if (!onRenderThread()) {
            MinecraftClient.getInstance().execute(() -> select(id));
            return;
        }
        try {
            readSetting();
            selected = MiniWitherSkinSettings.normalize(id);
            MiniWitherSkinSettings.write(selected);
            if (!initialized || (!selected.isEmpty() && !folders.containsKey(selected))) {
                rescan();
            }
            initialized = true;
            loadSelected();
        } catch (Exception | LinkageError | StackOverflowError failure) {
            fail(describe(failure), failure);
        }
    }

    /** Rescans the folders and reloads the selected skin from disk. / 重新扫描并从磁盘重新加载选中的皮肤。 */
    public static void reload() {
        if (!onRenderThread()) {
            MinecraftClient.getInstance().execute(MiniWitherSkinManager::reload);
            return;
        }
        try {
            readSetting();
            rescan();
            initialized = true;
            loadSelected();
        } catch (Exception | LinkageError | StackOverflowError failure) {
            fail(describe(failure), failure);
        }
    }

    /** The loaded selected skin; null draws and plays vanilla. / 已加载的选中皮肤；null 时绘制与播放原版。 */
    @Nullable
    public static MiniWitherSkin active() {
        if (!initialized) {
            ensureInitialized();
        }
        return active;
    }

    public static Status status() {
        ensureInitialized();
        return status;
    }

    /**
     * Non-fatal problems of the last load (missing animation or bone names, ignored keys and files), plain English.
     * 上次加载的非致命问题（缺失的动画或骨骼名、被忽略的键与文件），英文。
     */
    public static List<String> warnings() {
        ensureInitialized();
        return warnings;
    }

    /**
     * A draw of the active skin threw: log once, drop it to vanilla until the next {@link #reload()} or selection.
     * 当前皮肤绘制时抛出异常：只记录一次日志，并回退到原版，直到下一次重新加载或重新选择。
     *
     * <p>Its textures stay registered until that next load, because vertices drawn earlier this frame may still
     * reference them. 其贴图保留到下次加载，因为本帧早先提交的顶点可能仍引用它们。
     */
    public static void markBroken(Throwable failure) {
        MiniWitherSkin skin = active;
        if (skin == null) {
            return;
        }
        active = null;
        status = new Status(Status.Kind.BROKEN, "");
        SparkWitch.LOGGER.error("The Mini Wither skin \"{}\" failed to draw; using the vanilla look until it is"
                + " reloaded", skin.id(), failure);
        clearSounds();
    }

    private static void ensureInitialized() {
        if (initialized || !onRenderThread()) {
            return;
        }
        initialized = true;
        try {
            readSetting();
            rescan();
            loadSelected();
        } catch (Exception | LinkageError | StackOverflowError failure) {
            fail(describe(failure), failure);
        }
    }

    private static void readSetting() {
        if (!settingRead) {
            settingRead = true;
            selected = MiniWitherSkinSettings.read();
        }
    }

    private static void rescan() {
        List<MiniWitherSkinFolder.Found> found = MiniWitherSkinFolder.scan(skinsDirectory());
        List<Entry> nextEntries = new ArrayList<>();
        Map<String, Path> nextFolders = new LinkedHashMap<>();
        for (MiniWitherSkinFolder.Found skin : found) {
            nextEntries.add(new Entry(skin.id(), skin.displayName()));
            nextFolders.put(skin.id(), skin.folder());
        }
        entries = List.copyOf(nextEntries);
        folders = nextFolders;
    }

    private static void loadSelected() {
        unload();
        if (selected.isEmpty()) {
            status = DEFAULT_STATUS;
            clearSounds();
            return;
        }
        Path folder = folders.get(selected);
        if (folder == null) {
            status = new Status(Status.Kind.MISSING, "");
            SparkWitch.LOGGER.info("The Mini Wither skin \"{}\" is not in {}; using the vanilla look", selected,
                    skinsDirectory());
            clearSounds();
            return;
        }
        try {
            MiniWitherSkinSpec spec = MiniWitherSkinFolder.resolve(selected, folder,
                    BedrockGeometryParser::isGeometryFile);
            MiniWitherSkinLoader.Loaded loaded = MiniWitherSkinLoader.load(spec, ++generation);
            textures = loaded.textures();
            warnings = loaded.warnings();
            if (spec.sounds().isEmpty()) {
                MiniWitherSoundOverrides.clear();
            } else {
                MiniWitherSoundOverrides.update(spec.id(), spec.sounds());
            }
            active = loaded.skin();
            status = new Status(Status.Kind.LOADED, "");
            for (String warning : warnings) {
                SparkWitch.LOGGER.warn("Mini Wither skin \"{}\": {}", spec.id(), warning);
            }
        } catch (InvalidSkinException failure) {
            fail(failure.getMessage(), failure);
        } catch (Exception | LinkageError | StackOverflowError failure) {
            fail(describe(failure), failure);
        }
    }

    /** Drops the current skin: textures freed, vanilla drawn. / 卸下当前皮肤：释放贴图，绘制原版。 */
    private static void unload() {
        active = null;
        warnings = List.of();
        List<Identifier> owned = textures;
        textures = List.of();
        MiniWitherSkinLoader.destroy(owned);
    }

    private static void fail(String detail, Throwable failure) {
        try {
            unload();
        } catch (RuntimeException exception) {
            failure.addSuppressed(exception);
        }
        status = new Status(Status.Kind.ERROR, detail);
        SparkWitch.LOGGER.warn("Unable to load the Mini Wither skin \"{}\": {}", selected, detail, failure);
        clearSounds();
    }

    private static void clearSounds() {
        try {
            MiniWitherSoundOverrides.clear();
        } catch (Exception | LinkageError exception) {
            SparkWitch.LOGGER.warn("Unable to clear the Mini Wither skin sounds", exception);
        }
    }

    private static String describe(Throwable failure) {
        if (failure instanceof InvalidSkinException) {
            return failure.getMessage();
        }
        String message = failure.getMessage();
        String name = failure.getClass().getSimpleName();
        return message == null || message.isBlank() ? name : name + ": " + message;
    }

    private static boolean onRenderThread() {
        return RenderSystem.isOnRenderThread();
    }

    public record Entry(String id, String displayName) {
    }

    /**
     * What the settings screen shows. {@code detail} is untranslated: the English error text for {@link Kind#ERROR},
     * otherwise empty.
     * 设置界面显示的状态。{@code detail} 不翻译：{@link Kind#ERROR} 时为英文错误信息，否则为空。
     */
    public record Status(Kind kind, String detail) {
        public enum Kind {
            DEFAULT,
            LOADED,
            MISSING,
            ERROR,
            BROKEN
        }
    }
}
