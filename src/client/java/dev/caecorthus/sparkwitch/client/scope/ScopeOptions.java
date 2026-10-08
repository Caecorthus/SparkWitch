package dev.caecorthus.sparkwitch.client.scope;

import com.mojang.serialization.Codec;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.OptionListWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Consumer;

/**
 * Vanilla {@link SimpleOption}s for the scope settings, shown at the end of Options → Accessibility (owner decision Q3:
 * not Video Settings, which Sodium replaces) by {@code ScopeAccessibilityOptionsMixin}: Scope View with Lens Resolution
 * beside it, then Scoped Sensitivity. Built fresh each time the screen initialises, reading
 * {@link ScopeSettingsStore#current()}; each change is saved at once. They are never part of {@code GameOptions}, so
 * {@code options.txt} is untouched. Lens Resolution only affects Picture-in-Picture: its button is inactive (greyed)
 * while Full-Screen Zoom is selected and follows the Scope View button at once; its tooltip says so as well.
 * 开镜设置对应的原版 {@link SimpleOption}，由 {@code ScopeAccessibilityOptionsMixin} 显示在「选项 → 辅助功能」末尾（所有者决定
 * Q3：不放视频设置，因为 Sodium 会替换它）：「开镜画面」右侧为「镜内分辨率」，下一行为「开镜灵敏度」。每次界面初始化时读取
 * {@link ScopeSettingsStore#current()} 重新创建；每次修改立即保存。它们从不属于 {@code GameOptions}，因此不改动
 * {@code options.txt}。镜内分辨率只影响画中画：选择全画面放大时其按钮不可用（变灰），并随「开镜画面」按钮即时切换；其提示
 * 文本也说明了这一点。
 */
public final class ScopeOptions {
    public static final String MODE_KEY = "option.sparkwitch.scope_mode";
    public static final String SENSITIVITY_KEY = "option.sparkwitch.scope_sensitivity";
    public static final String SENSITIVITY_TOOLTIP_KEY = "option.sparkwitch.scope_sensitivity.tooltip";
    public static final String LENS_RESOLUTION_KEY = "option.sparkwitch.scope_lens_resolution";
    public static final String LENS_RESOLUTION_TOOLTIP_KEY = "option.sparkwitch.scope_lens_resolution.tooltip";
    private static final Codec<ScopeMode> MODE_CODEC = Codec.STRING.xmap(ScopeSettings::parseMode,
            ScopeSettings::serializedName);

    private ScopeOptions() {
    }

    /**
     * Appends the scope rows to an Accessibility screen's option list (one {@code addAll}) and links Lens Resolution's
     * button to the Scope View choice. The options and the link live as long as that screen's widgets.
     * 把开镜选项行追加到辅助功能界面的选项列表（一次 {@code addAll}），并让镜内分辨率按钮跟随开镜画面的选择。选项与这一
     * 关联的生命周期与该界面的控件相同。
     */
    public static void addTo(OptionListWidget body) {
        SimpleOption<Integer> lensResolution = lensResolutionOption();
        SimpleOption<ScopeMode> mode = modeOption(selected -> syncLensResolution(body, lensResolution, selected));
        body.addAll(mode, lensResolution, sensitivityOption());
        syncLensResolution(body, lensResolution, ScopeSettingsStore.current().mode());
    }

    /**
     * @param afterChange runs after the new mode is saved / 新模式保存后执行
     */
    public static SimpleOption<ScopeMode> modeOption(Consumer<ScopeMode> afterChange) {
        return new SimpleOption<>(
                MODE_KEY,
                mode -> Tooltip.of(Text.translatable(modeTooltipKey(mode))),
                (prefix, mode) -> Text.translatable(modeKey(mode)),
                new SimpleOption.PotentialValuesBasedCallbacks<>(List.of(ScopeMode.values()), MODE_CODEC),
                ScopeSettingsStore.current().mode(),
                mode -> {
                    ScopeSettingsStore.setMode(mode);
                    afterChange.accept(mode);
                });
    }

    /**
     * Picture-in-Picture lens render size, cycling 50 % / 75 % / 100 % of the native lens size
     * ({@link ScopeRules#lensViewSize}).
     * 画中画镜内渲染尺寸，在原生镜片尺寸的 50 % / 75 % / 100 % 之间循环（{@link ScopeRules#lensViewSize}）。
     */
    public static SimpleOption<Integer> lensResolutionOption() {
        return new SimpleOption<>(
                LENS_RESOLUTION_KEY,
                SimpleOption.constantTooltip(Text.translatable(LENS_RESOLUTION_TOOLTIP_KEY)),
                (prefix, percent) -> Text.literal(percent + "%"),
                new SimpleOption.PotentialValuesBasedCallbacks<>(ScopeSettings.LENS_RESOLUTION_STEPS, Codec.INT),
                ScopeSettingsStore.current().lensResolutionPercent(),
                ScopeSettingsStore::setLensResolutionPercent);
    }

    /**
     * 10 %-200 % in 5 % steps; the slider value is the step count, applied when the slider is released.
     * 10 %-200 %，步长 5 %；滑块值为步数，松开滑块时生效。
     */
    public static SimpleOption<Integer> sensitivityOption() {
        int step = ScopeSettings.SENSITIVITY_STEP_PERCENT;
        return new SimpleOption<>(
                SENSITIVITY_KEY,
                SimpleOption.constantTooltip(Text.translatable(SENSITIVITY_TOOLTIP_KEY)),
                (prefix, steps) -> Text.translatable("options.percent_value", prefix, steps * step),
                new SimpleOption.ValidatingIntSliderCallbacks(ScopeSettings.MIN_SENSITIVITY_PERCENT / step,
                        ScopeSettings.MAX_SENSITIVITY_PERCENT / step, false),
                ScopeSettingsStore.current().sensitivityPercent() / step,
                steps -> ScopeSettingsStore.setSensitivityPercent(steps * step));
    }

    public static String modeKey(ScopeMode mode) {
        return MODE_KEY + "." + ScopeSettings.serializedName(mode);
    }

    public static String modeTooltipKey(ScopeMode mode) {
        return modeKey(mode) + ".tooltip";
    }

    /** Lens Resolution is clickable only while Picture-in-Picture is selected. / 仅在选择画中画时可点击。 */
    public static boolean lensResolutionActive(ScopeMode selected) {
        return selected == ScopeMode.PICTURE_IN_PICTURE;
    }

    private static void syncLensResolution(OptionListWidget body, SimpleOption<Integer> lensResolution,
                                           ScopeMode selected) {
        ClickableWidget widget = body.getWidgetFor(lensResolution);
        if (widget != null) {
            widget.active = lensResolutionActive(selected);
        }
    }
}
