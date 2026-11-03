package dev.caecorthus.sparkwitch.api.client;

import dev.caecorthus.sparkwitch.client.witchmaiden.skin.MiniWitherSkinManager;
import dev.caecorthus.sparkwitch.client.witchmaiden.skin.screen.MiniWitherSkinScreen;
import dev.caecorthus.sparkwitch.client.witchmaiden.skin.screen.MiniWitherSkinUi;
import net.minecraft.client.gui.screen.Screen;

import java.util.List;

/**
 * Stable cross-mod contract (client only, 2026-11-02): lets SparkAssist's SparkDLC settings screen show and change the
 * Mini Wither appearance (小凋灵外观) with its own widgets, or open SparkWitch's own screen. SparkAssist reaches it by
 * reflection, so this FQCN and the JDK-only signatures are frozen once shipped. It reads and changes the same live
 * state as Options → Skin Customization → Mini Wither Appearance ({@code MiniWitherSkinManager}), so the two never
 * disagree. The setting is local only (owner 2026-11-02): it changes how every Mini Wither looks and sounds on this
 * client and is never synced or sent to the server. {@link #selectSkin} applies and saves at once, so there is no
 * separate save. Call on the client thread once the client has started.
 * <p>
 * Translation keys, as on SparkWitch's screen: the title {@code screen.sparkwitch.mini_wither_skin.title}, the
 * default's name {@code screen.sparkwitch.mini_wither_skin.default}, the buttons
 * {@code screen.sparkwitch.mini_wither_skin.open_folder} and {@code screen.sparkwitch.mini_wither_skin.reload} (each
 * with a {@code .tooltip}), and the statuses {@code screen.sparkwitch.mini_wither_skin.status.<kind>}.
 * 稳定跨模组契约（仅客户端，2026-11-02）：允许 SparkAssist 的 SparkDLC 设置界面用自己的控件显示并修改小凋灵外观，或打开
 * SparkWitch 自己的界面。SparkAssist 通过反射访问本类，因此一经发布，完整类名与仅使用 JDK 类型的签名即冻结。它读写的是与
 * 「选项 → 皮肤自定义 → 小凋灵外观」相同的实时状态（{@code MiniWitherSkinManager}），两者永不冲突。该设置仅在本地生效（所有者
 * 2026-11-02）：只改变本客户端上所有小凋灵的外观与声音，从不同步或发送到服务器。{@link #selectSkin} 立即应用并保存，因此没有单独
 * 的保存方法。请在客户端启动后于客户端线程调用。
 * <p>
 * 翻译键与 SparkWitch 的界面相同：标题 {@code screen.sparkwitch.mini_wither_skin.title}、默认项名称
 * {@code screen.sparkwitch.mini_wither_skin.default}、按钮 {@code screen.sparkwitch.mini_wither_skin.open_folder} 与
 * {@code screen.sparkwitch.mini_wither_skin.reload}（各有 {@code .tooltip}），以及状态
 * {@code screen.sparkwitch.mini_wither_skin.status.<kind>}。
 */
public final class MiniWitherSkinSettingsApi {
    private MiniWitherSkinSettingsApi() {
    }

    /**
     * Every choice in display order: {@code ""} (the vanilla Wither) first, then each skin folder's id from the last
     * scan, sorted; an unmodifiable list. Call {@link #reloadSkins()} to pick up folders added since.
     * 按显示顺序列出全部可选项：{@code ""}（原版凋灵）在首位，然后是上次扫描到的各皮肤文件夹 id（已排序）；返回不可修改的
     * 列表。之后新增的文件夹需调用 {@link #reloadSkins()} 才会出现。
     */
    public static List<String> skinIds() {
        return MiniWitherSkinUi.ids();
    }

    /**
     * The name to show for {@code id} in the current language: "Default (Wither)" for {@code ""} or null, a skin's own
     * name ({@code skin.json} {@code "name"}, else its id), otherwise {@code id} itself.
     * {@code id} 在当前语言下的显示名称：{@code ""} 或 null 为「默认（凋灵）」，皮肤为其自身名称（{@code skin.json} 的
     * {@code "name"}，否则为 id），其他情况为 {@code id} 本身。
     */
    public static String skinDisplayName(String id) {
        return MiniWitherSkinUi.displayName(id).getString();
    }

    /**
     * The selected id, {@code ""} for the vanilla Wither. It may name a folder that no longer exists; then
     * {@link #statusText()} says so and the vanilla Wither is shown.
     * 当前选中的 id，原版凋灵为 {@code ""}。它可能指向已不存在的文件夹；此时 {@link #statusText()} 会说明，并显示原版凋灵。
     */
    public static String selectedSkin() {
        return MiniWitherSkinManager.selectedId();
    }

    /**
     * Selects {@code id}, one of {@link #skinIds()}: it is loaded, applied and saved to
     * {@code config/sparkwitch-client.json} at once. Null and ids not in {@link #skinIds()} are ignored. A skin that
     * fails to load stays selected and {@link #statusText()} shows the error.
     * 选择 {@code id}（{@link #skinIds()} 之一）：立即加载、应用并保存到 {@code config/sparkwitch-client.json}。null 与不在
     * {@link #skinIds()} 中的 id 会被忽略。加载失败的皮肤仍保持选中，由 {@link #statusText()} 显示错误。
     */
    public static void selectSkin(String id) {
        MiniWitherSkinUi.selectListed(id);
    }

    /**
     * Rescans the skins folder and reloads the selected skin from disk.
     * 重新扫描皮肤文件夹，并从磁盘重新加载选中的皮肤。
     */
    public static void reloadSkins() {
        MiniWitherSkinManager.reload();
    }

    /**
     * Opens {@code config/sparkwitch/mini_wither/} in the system file manager, creating it if needed. A failure is
     * logged, never thrown.
     * 在系统文件管理器中打开 {@code config/sparkwitch/mini_wither/}，必要时先创建。失败只记录日志，不抛出。
     */
    public static void openSkinsFolder() {
        MiniWitherSkinUi.openSkinsFolder();
    }

    /**
     * One line describing the current state in the current language: using the vanilla Wither, loaded, folder
     * missing, load error (with an English detail) or broken while drawing.
     * 以当前语言描述当前状态的一行文字：使用原版凋灵、已加载、文件夹不存在、加载出错（附英文详情）或绘制时出错。
     */
    public static String statusText() {
        return MiniWitherSkinUi.statusText().getString();
    }

    /**
     * A new SparkWitch Mini Wither appearance screen, typed as {@code Object} to keep this contract JDK-only; pass it
     * to {@code MinecraftClient#setScreen}. Closing it (Done or Esc) returns to {@code parent}, a
     * {@code net.minecraft.client.gui.screen.Screen} or null.
     * 新建一个 SparkWitch 小凋灵外观界面，以 {@code Object} 类型返回以保持本契约只用 JDK 类型；把它传给
     * {@code MinecraftClient#setScreen}。关闭（完成或 Esc）时回到 {@code parent}，它须为
     * {@code net.minecraft.client.gui.screen.Screen} 或 null。
     *
     * @throws IllegalArgumentException if {@code parent} is neither null nor a {@code Screen} / {@code parent} 既不是 null
     *                                  也不是 {@code Screen} 时抛出
     */
    public static Object createScreen(Object parent) {
        if (parent != null && !(parent instanceof Screen)) {
            throw new IllegalArgumentException("parent must be a Screen or null, not " + parent.getClass().getName());
        }
        return new MiniWitherSkinScreen((Screen) parent);
    }
}
