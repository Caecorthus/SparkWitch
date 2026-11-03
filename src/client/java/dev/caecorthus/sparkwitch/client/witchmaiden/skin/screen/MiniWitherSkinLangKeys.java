package dev.caecorthus.sparkwitch.client.witchmaiden.skin.screen;

import dev.caecorthus.sparkwitch.client.witchmaiden.skin.MiniWitherSkinManager;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherSoundSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every translation key of the Mini Wither appearance settings (screen, Skin Customization button, statuses, sound
 * slots), all under {@link #PREFIX}. {@link #all()} is the complete set, so the lang files can be checked against it.
 * 小凋灵外观设置的全部翻译键（界面、皮肤自定义按钮、状态、声音槽位），均以 {@link #PREFIX} 开头。{@link #all()} 是完整集合，
 * 可据此核对语言文件。
 */
public final class MiniWitherSkinLangKeys {
    public static final String PREFIX = "screen.sparkwitch.mini_wither_skin.";
    public static final String TITLE = PREFIX + "title";
    /** The button in Options → Skin Customization. / 「选项 → 皮肤自定义」中的按钮。 */
    public static final String ENTRY = PREFIX + "entry";
    public static final String ENTRY_TOOLTIP = PREFIX + "entry.tooltip";
    /** The "Default (Wither)" row: the vanilla Wither, no skin. / 「默认（凋灵）」一行：原版凋灵，无皮肤。 */
    public static final String DEFAULT_SKIN = PREFIX + "default";
    /** Muted line under the preview stage. / 预览舞台下方的灰色说明。 */
    public static final String CAPTION = PREFIX + "caption";
    /** Hint under the default row when there are no skins, {@code %s} = the skins folder. / 没有皮肤时的提示。 */
    public static final String EMPTY = PREFIX + "empty";
    /** Status tooltip header over the load warnings, {@code %s} = their count. / 状态提示中警告列表的标题。 */
    public static final String WARNINGS = PREFIX + "status.warnings";
    /** Last status tooltip line when warnings were left out, {@code %s} = how many. / 省略警告时的最后一行。 */
    public static final String MORE_WARNINGS = PREFIX + "status.more_warnings";
    /** Second tooltip line of a sound button, {@code %s} = the slot's file stem. / 声音按钮提示的第二行。 */
    public static final String SOUND_FILE = PREFIX + "sound.file";
    public static final String OPEN_FOLDER = PREFIX + "open_folder";
    public static final String OPEN_FOLDER_TOOLTIP = PREFIX + "open_folder.tooltip";
    public static final String RELOAD = PREFIX + "reload";
    public static final String RELOAD_TOOLTIP = PREFIX + "reload.tooltip";
    public static final String PREVIEW_UNAVAILABLE = PREFIX + "preview_unavailable";

    private MiniWitherSkinLangKeys() {
    }

    /**
     * The status line. DEFAULT takes no argument; LOADED, MISSING and BROKEN take the skin's name (MISSING: its id);
     * ERROR takes the name and the untranslated detail.
     * 状态行。DEFAULT 无参数；LOADED、MISSING、BROKEN 取皮肤名称（MISSING 为其 id）；ERROR 取名称与未翻译的详情。
     */
    public static String status(MiniWitherSkinManager.Status.Kind kind) {
        return PREFIX + "status." + kind.name().toLowerCase(Locale.ROOT);
    }

    public static String sound(MiniWitherSoundSlot slot) {
        return PREFIX + "sound." + slot.key();
    }

    public static String soundTooltip(MiniWitherSoundSlot slot) {
        return sound(slot) + ".tooltip";
    }

    /** Every key above, in a stable order. / 上述全部键，顺序固定。 */
    public static List<String> all() {
        List<String> keys = new ArrayList<>(List.of(TITLE, ENTRY, ENTRY_TOOLTIP, DEFAULT_SKIN, CAPTION, EMPTY, WARNINGS,
                MORE_WARNINGS, SOUND_FILE, OPEN_FOLDER, OPEN_FOLDER_TOOLTIP, RELOAD, RELOAD_TOOLTIP,
                PREVIEW_UNAVAILABLE));
        for (MiniWitherSkinManager.Status.Kind kind : MiniWitherSkinManager.Status.Kind.values()) {
            keys.add(status(kind));
        }
        for (MiniWitherSoundSlot slot : MiniWitherSoundSlot.values()) {
            keys.add(sound(slot));
            keys.add(soundTooltip(slot));
        }
        return List.copyOf(keys);
    }
}
