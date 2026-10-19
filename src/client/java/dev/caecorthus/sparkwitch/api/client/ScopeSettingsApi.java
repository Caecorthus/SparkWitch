package dev.caecorthus.sparkwitch.api.client;

import dev.caecorthus.sparkwitch.client.scope.ScopeClient;
import dev.caecorthus.sparkwitch.client.scope.ScopeMode;
import dev.caecorthus.sparkwitch.client.scope.ScopeSettings;
import dev.caecorthus.sparkwitch.client.scope.ScopeSettingsStore;

import java.util.Arrays;
import java.util.List;

/**
 * Stable cross-mod contract (client only, 2026-10-19): lets SparkAssist's SparkDLC settings screen show and change the
 * three scope settings (Scope View, Lens Resolution, Scoped Sensitivity) with its own widgets. SparkAssist reaches it
 * by reflection, so this FQCN and the JDK-only signatures must not change. It reads and changes the same live settings
 * as Options → Accessibility ({@code ScopeSettingsStore}), so the two never disagree. Setters take effect in game at
 * once but never write {@code config/sparkwitch-client.json}; {@link #save()} does (on slider release, page switch or
 * screen close). Values are clamped and snapped as the file is; a mode or lens resolution that is not one of its
 * choices is ignored. Safe to call before SparkWitch's scope module registered: the first call loads the file. Call
 * on the client thread.
 * <p>
 * Translation keys, as on the vanilla screen: {@code option.sparkwitch.scope_mode} (Scope View), each mode's
 * {@code option.sparkwitch.scope_mode.<name>} and {@code option.sparkwitch.scope_mode.<name>.tooltip};
 * {@code option.sparkwitch.scope_lens_resolution} and its {@code .tooltip}; {@code option.sparkwitch.scope_sensitivity}
 * and its {@code .tooltip}.
 * 稳定跨模组契约（仅客户端，2026-10-19）：允许 SparkAssist 的 SparkDLC 设置界面用自己的控件显示并修改三项开镜设置
 * （开镜画面、镜内分辨率、开镜灵敏度）。SparkAssist 通过反射访问本类，因此完整类名与仅使用 JDK 类型的签名都不得更改。
 * 它读写的是与「选项 → 辅助功能」相同的实时设置（{@code ScopeSettingsStore}），两者永不冲突。设置方法在游戏中立即生效，
 * 但从不写入 {@code config/sparkwitch-client.json}；由 {@link #save()} 写入（松开滑块、切换页面或关闭界面时）。数值的钳制与
 * 取整与文件一致；不属于可选项的模式或镜内分辨率会被忽略。在 SparkWitch 开镜模块注册之前调用也安全：第一次调用会加载文件。
 * 请在客户端线程调用。
 * <p>
 * 翻译键与原版界面相同：{@code option.sparkwitch.scope_mode}（开镜画面）、各模式的 {@code option.sparkwitch.scope_mode.<name>}
 * 与 {@code option.sparkwitch.scope_mode.<name>.tooltip}；{@code option.sparkwitch.scope_lens_resolution} 及其
 * {@code .tooltip}；{@code option.sparkwitch.scope_sensitivity} 及其 {@code .tooltip}。
 */
public final class ScopeSettingsApi {
    private ScopeSettingsApi() {
    }

    /** The selected mode's serialized name, e.g. {@code "zoom_blur"}. / 当前所选模式的序列化名称。 */
    public static String scopeMode() {
        return ScopeSettings.serializedName(settings().mode());
    }

    /**
     * Selects the mode named {@code serializedName}, one of {@link #scopeModes()}; anything else (null included) is
     * ignored.
     * 选择名为 {@code serializedName} 的模式（{@link #scopeModes()} 之一）；其他值（包括 null）会被忽略。
     */
    public static void setScopeMode(String serializedName) {
        ScopeSettingsStore.load();
        for (ScopeMode mode : ScopeMode.values()) {
            if (ScopeSettings.serializedName(mode).equals(serializedName)) {
                ScopeSettingsStore.applyMode(mode);
                return;
            }
        }
    }

    /**
     * Every mode's serialized name in display order (the vanilla button's cycle order); an unmodifiable list.
     * 所有模式的序列化名称，按显示顺序（原版按钮的循环顺序）；返回不可修改的列表。
     */
    public static List<String> scopeModes() {
        return Arrays.stream(ScopeMode.values()).map(ScopeSettings::serializedName).toList();
    }

    public static String defaultScopeMode() {
        return ScopeSettings.serializedName(ScopeSettings.DEFAULTS.mode());
    }

    /**
     * True while Picture-in-Picture is selected but renders as Full-Screen Zoom right now: an Iris shader pack,
     * Fabulous! graphics, the Blind echo view, or the PiP renderer backing off after a failure. False while Full-Screen
     * Zoom is selected.
     * 选择了画中画、但当前按全画面放大渲染时为 true：Iris 光影包、「极佳」画质、盲人回声视图，或画中画渲染器失败后的退避期间。
     * 选择全画面放大时为 false。
     */
    public static boolean pictureInPictureSuppressed() {
        return settings().mode() == ScopeMode.PICTURE_IN_PICTURE && ScopeClient.effectiveMode() == ScopeMode.ZOOM_BLUR;
    }

    /** Picture-in-Picture lens render size, % of native. / 画中画镜内渲染尺寸，原生尺寸的百分比。 */
    public static int lensResolutionPercent() {
        return settings().lensResolutionPercent();
    }

    /**
     * Sets the lens resolution to {@code percent}, one of {@link #lensResolutionChoices()}; any other value is ignored.
     * 把镜内分辨率设为 {@code percent}（{@link #lensResolutionChoices()} 之一）；其他值会被忽略。
     */
    public static void setLensResolutionPercent(int percent) {
        ScopeSettingsStore.load();
        if (ScopeSettings.LENS_RESOLUTION_STEPS.contains(percent)) {
            ScopeSettingsStore.applyLensResolutionPercent(percent);
        }
    }

    /** The accepted lens resolutions in cycle order; a new array each call. / 可选镜内分辨率，按循环顺序；每次返回新数组。 */
    public static int[] lensResolutionChoices() {
        return ScopeSettings.LENS_RESOLUTION_STEPS.stream().mapToInt(Integer::intValue).toArray();
    }

    public static int defaultLensResolutionPercent() {
        return ScopeSettings.DEFAULT_LENS_RESOLUTION_PERCENT;
    }

    /** Mouse-look scale while scoped, %. / 开镜时的鼠标视角缩放，百分比。 */
    public static int scopedSensitivityPercent() {
        return settings().sensitivityPercent();
    }

    /**
     * Sets the scoped sensitivity, clamped to [{@link #scopedSensitivityMin()}, {@link #scopedSensitivityMax()}] and
     * rounded to the nearest {@link #scopedSensitivityStep()}.
     * 设置开镜灵敏度，钳制到 [{@link #scopedSensitivityMin()}, {@link #scopedSensitivityMax()}] 并取最近的
     * {@link #scopedSensitivityStep()} 倍数。
     */
    public static void setScopedSensitivityPercent(int percent) {
        ScopeSettingsStore.load();
        ScopeSettingsStore.applySensitivityPercent(percent);
    }

    public static int scopedSensitivityMin() {
        return ScopeSettings.MIN_SENSITIVITY_PERCENT;
    }

    public static int scopedSensitivityMax() {
        return ScopeSettings.MAX_SENSITIVITY_PERCENT;
    }

    public static int scopedSensitivityStep() {
        return ScopeSettings.SENSITIVITY_STEP_PERCENT;
    }

    public static int defaultScopedSensitivityPercent() {
        return ScopeSettings.DEFAULT_SENSITIVITY_PERCENT;
    }

    /**
     * Writes the settings changed through this API to {@code config/sparkwitch-client.json}; does nothing when none is
     * unsaved. An I/O error is logged, never thrown, and the next call retries.
     * 把经本 API 修改的设置写入 {@code config/sparkwitch-client.json}；没有未保存的修改时什么也不做。I/O 错误只记录日志、
     * 不抛出，下一次调用会重试。
     */
    public static void save() {
        ScopeSettingsStore.load();
        ScopeSettingsStore.save();
    }

    private static ScopeSettings settings() {
        ScopeSettingsStore.load();
        return ScopeSettingsStore.current();
    }
}
