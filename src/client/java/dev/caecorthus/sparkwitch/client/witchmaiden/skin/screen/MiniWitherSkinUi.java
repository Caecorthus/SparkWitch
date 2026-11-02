package dev.caecorthus.sparkwitch.client.witchmaiden.skin.screen;

import dev.caecorthus.sparkwitch.client.witchmaiden.skin.MiniWitherSkinManager;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * What the Mini Wither appearance settings show and do, shared by {@link MiniWitherSkinScreen} and
 * {@code api.client.MiniWitherSkinSettingsApi} so both always agree: the choices ({@link MiniWitherSkinManager#DEFAULT_ID}
 * first), their names, a listed-only selection, the translated status line and opening the skins folder. Client-only
 * presentation: nothing here is synced or sent to the server. Client thread only.
 * 小凋灵外观设置显示与执行的内容，由 {@link MiniWitherSkinScreen} 与 {@code api.client.MiniWitherSkinSettingsApi} 共用，二者始终
 * 一致：可选项（{@link MiniWitherSkinManager#DEFAULT_ID} 在首位）、名称、只接受列表内 id 的选择、已翻译的状态行，以及打开皮肤
 * 文件夹。纯客户端表现：这里的任何内容都不会同步或发送到服务器。仅限客户端线程。
 */
public final class MiniWitherSkinUi {
    private static final Logger LOGGER = LoggerFactory.getLogger("SparkWitch/MiniWitherSkin");

    private MiniWitherSkinUi() {
    }

    /**
     * The skin folders of the last scan, sorted by id. The manager scans on its first {@link MiniWitherSkinManager#active()}
     * call, so this asks for it first: the list is complete even before any Mini Wither was drawn.
     * 上次扫描到的皮肤文件夹，按 id 排序。管理器在第一次 {@link MiniWitherSkinManager#active()} 调用时扫描，因此这里先调用它：
     * 即使还没有绘制过任何小凋灵，列表也是完整的。
     */
    public static List<MiniWitherSkinManager.Entry> entries() {
        MiniWitherSkinManager.active();
        return MiniWitherSkinManager.entries();
    }

    /** {@link MiniWitherSkinManager#DEFAULT_ID} first, then every listed skin id. / 默认 id 在首位，然后是每个皮肤 id。 */
    public static List<String> ids() {
        List<String> ids = new ArrayList<>();
        ids.add(MiniWitherSkinManager.DEFAULT_ID);
        for (MiniWitherSkinManager.Entry entry : entries()) {
            ids.add(entry.id());
        }
        return List.copyOf(ids);
    }

    /**
     * "Default (Wither)" for the default (null reads as the default), a listed skin's own name, otherwise the id.
     * 默认项为「默认（凋灵）」（null 视为默认），列表内的皮肤用其名称，否则为 id 本身。
     */
    public static Text displayName(@Nullable String id) {
        if (id == null || id.equals(MiniWitherSkinManager.DEFAULT_ID)) {
            return Text.translatable(MiniWitherSkinLangKeys.DEFAULT_SKIN);
        }
        for (MiniWitherSkinManager.Entry entry : MiniWitherSkinManager.entries()) {
            if (entry.id().equals(id)) {
                return Text.literal(entry.displayName());
            }
        }
        return Text.literal(id);
    }

    /**
     * Selects {@code id} (applied and saved at once by the manager) when it is the default or a listed skin; null and
     * anything else are ignored, so an arbitrary string never reaches the config file.
     * 当 {@code id} 为默认或列表内的皮肤时选中它（由管理器立即应用并保存）；null 及其他值会被忽略，任意字符串都不会写入配置文件。
     *
     * @return whether the id was accepted / 是否接受了该 id
     */
    public static boolean selectListed(@Nullable String id) {
        if (id == null || !(id.equals(MiniWitherSkinManager.DEFAULT_ID) || listed(id))) {
            return false;
        }
        MiniWitherSkinManager.select(id);
        return true;
    }

    // The caller listed the ids first, so this reads the last scan without loading the outgoing skin.
    // 调用方已先列出 id，因此这里直接读取上次扫描结果，不会先加载即将被替换的皮肤。
    private static boolean listed(String id) {
        for (MiniWitherSkinManager.Entry entry : MiniWitherSkinManager.entries()) {
            if (entry.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** The status line in the current language, uncoloured. / 当前语言的状态行，不带颜色。 */
    public static Text statusText() {
        MiniWitherSkinManager.Status status = MiniWitherSkinManager.status();
        String selected = MiniWitherSkinManager.selectedId();
        String key = MiniWitherSkinLangKeys.status(status.kind());
        return switch (status.kind()) {
            case DEFAULT -> Text.translatable(key);
            case MISSING -> Text.translatable(key, selected);
            case ERROR -> Text.translatable(key, displayName(selected), status.detail());
            case LOADED, BROKEN -> Text.translatable(key, displayName(selected));
        };
    }

    /**
     * Opens the skins folder in the system file manager, creating it first if it was deleted. An I/O failure is logged,
     * never thrown.
     * 在系统文件管理器中打开皮肤文件夹；若已被删除则先创建。I/O 失败只记录日志，不抛出。
     */
    public static void openSkinsFolder() {
        entries();
        Path directory = MiniWitherSkinManager.skinsDirectory();
        try {
            Files.createDirectories(directory);
        } catch (IOException | RuntimeException failure) {
            LOGGER.warn("Could not create the Mini Wither skins folder {}", directory, failure);
            return;
        }
        Util.getOperatingSystem().open(directory);
    }
}
