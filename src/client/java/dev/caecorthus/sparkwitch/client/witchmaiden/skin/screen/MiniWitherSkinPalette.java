package dev.caecorthus.sparkwitch.client.witchmaiden.skin.screen;

import dev.caecorthus.sparkwitch.client.witchmaiden.skin.MiniWitherSkinManager;

/**
 * Colours of the Mini Wither appearance screen (owner pick 2026-11-02, mockup A "暗夜面板"): the flat translucent dark
 * look of SparkAssist's SparkDLC settings screen, with the Witch Maiden's magenta as the only accent. The neutral
 * values are copied from SparkAssist's {@code SettingsPalette}, because SparkWitch cannot depend on SparkAssist.
 * 小凋灵外观界面的颜色（所有者 2026-11-02 选定方案 A「暗夜面板」）：沿用 SparkAssist SparkDLC 设置界面扁平半透明的深色风格，
 * 以巫女洋红为唯一强调色。中性色复制自 SparkAssist 的 {@code SettingsPalette}，因为 SparkWitch 不能依赖 SparkAssist。
 */
public final class MiniWitherSkinPalette {
    public static final int PANEL = 0xB0181818;
    public static final int ROW_HOVER = 0xC82A2A2A;
    public static final int SEPARATOR = 0x40FFFFFF;
    public static final int STAGE_FRAME = 0x40FFFFFF;
    public static final int STAGE_FILL = 0x50101010;

    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_BODY = 0xFFD0D0D0;
    public static final int TEXT_MUTED = 0xFFA0A0A0;
    public static final int TEXT_DISABLED = 0xFF6E6E6E;

    public static final int BUTTON = 0xFF2F2F2F;
    public static final int BUTTON_HOVER = 0xFF464646;
    public static final int BUTTON_EDGE = 0xFF0E0E0E;

    public static final int SCROLL_TRACK = 0x40000000;
    public static final int SCROLL_THUMB = 0x90FFFFFF;

    /** Witch Maiden magenta, the one accent. / 巫女洋红，唯一的强调色。 */
    public static final int ACCENT = 0xFFB04A8B;
    public static final int ACCENT_HOVER = 0xFFC965A4;
    public static final int ACCENT_WASH = 0x48B04A8B;

    public static final int DOT_LOADED = 0xFF9BD67E;
    public static final int DOT_DEFAULT = 0xFF9A9A9A;
    public static final int DOT_WARNING = 0xFFE3B55A;
    public static final int DOT_ERROR = 0xFFE06A5F;

    private MiniWitherSkinPalette() {
    }

    /**
     * The status pill's dot: green loaded, grey default, amber missing or loaded with warnings, red error or broken.
     * 状态胶囊的圆点：已加载为绿色，默认为灰色，文件夹缺失或加载有警告为琥珀色，出错或损坏为红色。
     */
    public static int dot(MiniWitherSkinManager.Status.Kind kind, boolean warnings) {
        return switch (kind) {
            case DEFAULT -> warnings ? DOT_WARNING : DOT_DEFAULT;
            case LOADED -> warnings ? DOT_WARNING : DOT_LOADED;
            case MISSING -> DOT_WARNING;
            case ERROR, BROKEN -> DOT_ERROR;
        };
    }
}
