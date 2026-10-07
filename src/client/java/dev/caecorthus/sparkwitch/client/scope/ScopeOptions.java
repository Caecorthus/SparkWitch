package dev.caecorthus.sparkwitch.client.scope;

import com.mojang.serialization.Codec;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Vanilla {@link SimpleOption}s for the scope settings, shown at the end of Options → Accessibility (owner decision Q3:
 * not Video Settings, which Sodium replaces) by {@code ScopeAccessibilityOptionsMixin}. Built fresh each time the screen
 * initialises, reading {@link ScopeSettingsStore#current()}; each change is saved at once. They are never part of
 * {@code GameOptions}, so {@code options.txt} is untouched.
 * 开镜设置对应的原版 {@link SimpleOption}，由 {@code ScopeAccessibilityOptionsMixin} 显示在「选项 → 辅助功能」末尾（所有者决定
 * Q3：不放视频设置，因为 Sodium 会替换它）。每次界面初始化时读取 {@link ScopeSettingsStore#current()} 重新创建；每次修改立即
 * 保存。它们从不属于 {@code GameOptions}，因此不改动 {@code options.txt}。
 */
public final class ScopeOptions {
    public static final String MODE_KEY = "option.sparkwitch.scope_mode";
    public static final String SENSITIVITY_KEY = "option.sparkwitch.scope_sensitivity";
    public static final String SENSITIVITY_TOOLTIP_KEY = "option.sparkwitch.scope_sensitivity.tooltip";
    /** Appended to the PiP tooltip while PiP still renders as ZOOM_BLUR. / 画中画仍按全画面放大渲染期间追加到其提示。 */
    public static final String PICTURE_IN_PICTURE_PENDING_KEY = "option.sparkwitch.scope_mode.picture_in_picture.pending";
    private static final Codec<ScopeMode> MODE_CODEC = Codec.STRING.xmap(ScopeSettings::parseMode,
            ScopeSettings::serializedName);

    private ScopeOptions() {
    }

    /** Both options, in screen order. / 两个选项，按界面顺序。 */
    public static SimpleOption<?>[] all() {
        return new SimpleOption<?>[]{modeOption(), sensitivityOption()};
    }

    public static SimpleOption<ScopeMode> modeOption() {
        return new SimpleOption<>(
                MODE_KEY,
                ScopeOptions::modeTooltip,
                (prefix, mode) -> Text.translatable(modeKey(mode)),
                new SimpleOption.PotentialValuesBasedCallbacks<>(List.of(ScopeMode.values()), MODE_CODEC),
                ScopeSettingsStore.current().mode(),
                ScopeSettingsStore::setMode);
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

    private static Tooltip modeTooltip(ScopeMode mode) {
        MutableText text = Text.translatable(modeTooltipKey(mode));
        if (mode == ScopeMode.PICTURE_IN_PICTURE && !ScopeRules.PICTURE_IN_PICTURE_AVAILABLE) {
            text.append("\n").append(Text.translatable(PICTURE_IN_PICTURE_PENDING_KEY));
        }
        return Tooltip.of(text);
    }
}
