package dev.caecorthus.sparkwitch.client.compat;

import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.BooleanSupplier;

/**
 * Asks SparkAssist whether its SparkDLC settings screen shows SparkWitch's scope settings (through
 * {@code api.client.ScopeSettingsApi}), so Options → Accessibility leaves its own scope rows out. Resolved lazily, the
 * first time that screen initialises, by reflection against SparkAssist's public {@code SparkDlcSettingsApi}
 * ({@code boolean hostsScopeSettings()}), then asked again at every screen init. Without SparkAssist, with an older
 * SparkAssist that lacks the method, or after any failure (logged once, then off for the session) the answer is
 * false, and Accessibility shows the rows exactly as before. {@link GuideDecorBridge} precedent.
 * 询问 SparkAssist 的 SparkDLC 设置界面是否（通过 {@code api.client.ScopeSettingsApi}）显示 SparkWitch 的开镜设置，若是，
 * 「选项 → 辅助功能」就不再加入自己的开镜选项行。在该界面第一次初始化时才通过反射解析 SparkAssist 公开的
 * {@code SparkDlcSettingsApi}（{@code boolean hostsScopeSettings()}），之后每次界面初始化都重新询问。未安装 SparkAssist、
 * SparkAssist 版本较旧缺少该方法，或任一次失败之后（只记录一次日志，本次会话内停用）答案均为 false，辅助功能界面照旧显示
 * 这些选项行。参照 {@link GuideDecorBridge}。
 */
public final class SparkDlcSettingsBridge {
    static final String MOD_ID = "sparkassist";
    static final String API = "dev.caecorthus.sparkassist.client.settings.SparkDlcSettingsApi";
    static final String HOSTS_SCOPE_SETTINGS = "hostsScopeSettings";
    private static final Logger LOGGER = LoggerFactory.getLogger("SparkWitch/SparkDlcSettings");
    private static final SparkDlcSettingsBridge SPARK_ASSIST = new SparkDlcSettingsBridge(
            () -> FabricLoader.getInstance().isModLoaded(MOD_ID), API);

    private final BooleanSupplier installed;
    private final String apiClass;
    private boolean resolved;
    @Nullable
    private MethodHandle hostsScopeSettings;

    /**
     * @param installed whether SparkAssist is loaded; asked once, at the first {@link #hosts()} / 是否已加载 SparkAssist；
     *                  只在第一次 {@link #hosts()} 时询问一次
     * @param apiClass  binary name of the class declaring {@code public static boolean hostsScopeSettings()} /
     *                  声明该方法的类的二进制名称
     */
    SparkDlcSettingsBridge(BooleanSupplier installed, String apiClass) {
        this.installed = installed;
        this.apiClass = apiClass;
    }

    /**
     * Whether SparkAssist's settings screen hosts the scope settings; false whenever that cannot be confirmed.
     * SparkAssist 的设置界面是否承载开镜设置；无法确认时一律为 false。
     */
    public static boolean hostsScopeSettings() {
        return SPARK_ASSIST.hosts();
    }

    synchronized boolean hosts() {
        MethodHandle handle = resolve();
        if (handle == null) {
            return false;
        }
        try {
            return (boolean) handle.invokeExact();
        } catch (Throwable failure) {
            // The scope options must never vanish from both screens: keep them in Accessibility for the session.
            // 开镜选项绝不能在两个界面都消失：本次会话内保留在辅助功能界面。
            LOGGER.warn("SparkAssist's settings screen check failed; the scope options stay in Accessibility for this "
                    + "session", failure);
            hostsScopeSettings = null;
            return false;
        }
    }

    @Nullable
    private MethodHandle resolve() {
        if (resolved) {
            return hostsScopeSettings;
        }
        resolved = true;
        if (!installed.getAsBoolean()) {
            return null;
        }
        try {
            Class<?> api = Class.forName(apiClass, false, SparkDlcSettingsBridge.class.getClassLoader());
            hostsScopeSettings = MethodHandles.publicLookup().findStatic(api, HOSTS_SCOPE_SETTINGS,
                    MethodType.methodType(boolean.class));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            LOGGER.warn("SparkAssist is installed but its settings screen API is unavailable; the scope options stay "
                    + "in Accessibility", exception);
            hostsScopeSettings = null;
        }
        return hostsScopeSettings;
    }
}
