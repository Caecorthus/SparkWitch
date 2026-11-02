package dev.caecorthus.sparkwitch.client.witchmaiden.skin;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * Owns the Mini Wither skins: the skins directory ({@code config/sparkwitch/mini_wither/<id>/}), the selected id
 * (key {@code miniWitherSkin} in {@code config/sparkwitch-client.json}, merged like {@code ScopeSettingsStore}),
 * loading the selected folder, registering its textures and pushing its sounds to {@code MiniWitherSoundOverrides}.
 * Client thread only.
 * 管理小凋灵皮肤：皮肤目录（{@code config/sparkwitch/mini_wither/<id>/}）、选中的 id（保存在
 * {@code config/sparkwitch-client.json} 的 {@code miniWitherSkin} 键，与 {@code ScopeSettingsStore} 一样合并写入）、
 * 加载选中的文件夹、注册其贴图并把声音交给 {@code MiniWitherSoundOverrides}。仅限客户端线程。
 */
public final class MiniWitherSkinManager {
    // CONTRACT SKELETON (2026-11-02): the skin-store agent owns the implementation.

    /** The vanilla Wither (no skin). / 原版凋灵（无皮肤）。 */
    public static final String DEFAULT_ID = "";

    private MiniWitherSkinManager() {
    }

    /** Client init: reads the setting; the first {@link #active()} call scans and loads. / 客户端初始化。 */
    public static void register() {
        throw new UnsupportedOperationException("TODO");
    }

    public static Path skinsDirectory() {
        throw new UnsupportedOperationException("TODO");
    }

    /** Skin folders found by the last scan, sorted by id; never includes the default. / 上次扫描找到的皮肤文件夹。 */
    public static List<Entry> entries() {
        throw new UnsupportedOperationException("TODO");
    }

    public static String selectedId() {
        throw new UnsupportedOperationException("TODO");
    }

    /** Selects, loads at once and saves; an unknown id or a failed load keeps the id and shows the error. */
    public static void select(String id) {
        throw new UnsupportedOperationException("TODO");
    }

    /** Rescans the folders and reloads the selected skin from disk. / 重新扫描并从磁盘重新加载选中的皮肤。 */
    public static void reload() {
        throw new UnsupportedOperationException("TODO");
    }

    /** The loaded selected skin; null draws and plays vanilla. / 已加载的选中皮肤；null 时绘制与播放原版。 */
    @Nullable
    public static MiniWitherSkin active() {
        throw new UnsupportedOperationException("TODO");
    }

    public static Status status() {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * A draw of the active skin threw: log once, drop it to vanilla until the next {@link #reload()} or selection.
     * 当前皮肤绘制时抛出异常：只记录一次日志，并回退到原版，直到下一次重新加载或重新选择。
     */
    public static void markBroken(Throwable failure) {
        throw new UnsupportedOperationException("TODO");
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
