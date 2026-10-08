package dev.caecorthus.sparkwitch.client.scope;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * The player's scope settings and their JSON codec (keys {@value #MODE_KEY}, {@value #SENSITIVITY_KEY} and
 * {@value #LENS_RESOLUTION_KEY} in {@code config/sparkwitch-client.json}). Decoding is tolerant: a missing, mistyped or
 * unknown value falls back to its default, the sensitivity is clamped to 10-200 % and snapped to 5 % steps, and a lens
 * resolution that is not one of {@link #LENS_RESOLUTION_STEPS} means the default (100 %), so a file written before the
 * lens resolution existed loads unchanged. Writing only touches these three keys, so other client settings stored in
 * the same file survive. Pure; unit-tested.
 * 玩家的开镜设置及其 JSON 编解码（{@code config/sparkwitch-client.json} 中的 {@value #MODE_KEY}、{@value #SENSITIVITY_KEY}
 * 与 {@value #LENS_RESOLUTION_KEY}）。解码是宽容的：缺失、类型错误或未知的值回退为默认值，灵敏度钳制到 10-200 % 并按 5 %
 * 取整，不属于 {@link #LENS_RESOLUTION_STEPS} 的镜内分辨率视为默认值（100 %），因此镜内分辨率出现之前写入的文件照常加载。
 * 写入只改动这三个键，存放在同一文件中的其他客户端设置得以保留。纯逻辑，有单元测试。
 */
public record ScopeSettings(ScopeMode mode, int sensitivityPercent, int lensResolutionPercent) {
    public static final String MODE_KEY = "scopeMode";
    public static final String SENSITIVITY_KEY = "scopedSensitivityPercent";
    public static final String LENS_RESOLUTION_KEY = "scopeLensResolutionPercent";
    public static final int MIN_SENSITIVITY_PERCENT = 10;
    public static final int MAX_SENSITIVITY_PERCENT = 200;
    public static final int SENSITIVITY_STEP_PERCENT = 5;
    public static final int DEFAULT_SENSITIVITY_PERCENT = 100;
    /**
     * Picture-in-Picture lens render size as a share of the native lens size ({@link ScopeRules#lensViewSize}), in the
     * order the option cycles through them. Only these values are accepted.
     * 画中画镜内渲染尺寸占原生镜片尺寸（{@link ScopeRules#lensViewSize}）的百分比，按选项循环顺序排列。只接受这些值。
     */
    public static final List<Integer> LENS_RESOLUTION_STEPS = List.of(50, 75, 100);
    /** Native: one lens texel per screen pixel. / 原生：每个屏幕像素对应一个镜内纹素。 */
    public static final int DEFAULT_LENS_RESOLUTION_PERCENT = 100;
    public static final ScopeSettings DEFAULTS = new ScopeSettings(ScopeMode.ZOOM_BLUR, DEFAULT_SENSITIVITY_PERCENT,
            DEFAULT_LENS_RESOLUTION_PERCENT);

    public ScopeSettings {
        mode = mode == null ? ScopeMode.ZOOM_BLUR : mode;
        sensitivityPercent = clampPercent(sensitivityPercent);
        lensResolutionPercent = lensResolutionOrDefault(lensResolutionPercent);
    }

    public ScopeSettings withMode(ScopeMode newMode) {
        return new ScopeSettings(newMode, sensitivityPercent, lensResolutionPercent);
    }

    public ScopeSettings withSensitivityPercent(int newPercent) {
        return new ScopeSettings(mode, newPercent, lensResolutionPercent);
    }

    public ScopeSettings withLensResolutionPercent(int newPercent) {
        return new ScopeSettings(mode, sensitivityPercent, newPercent);
    }

    /** Clamped to [10, 200] and rounded to the nearest 5 %. / 钳制到 [10, 200] 并取最近的 5 %。 */
    public static int clampPercent(int percent) {
        int clamped = Math.max(MIN_SENSITIVITY_PERCENT, Math.min(MAX_SENSITIVITY_PERCENT, percent));
        return Math.round(clamped / (float) SENSITIVITY_STEP_PERCENT) * SENSITIVITY_STEP_PERCENT;
    }

    /**
     * One of {@link #LENS_RESOLUTION_STEPS}; any other value is unknown and means the default (100 %).
     * {@link #LENS_RESOLUTION_STEPS} 之一；其他值视为未知，按默认值（100 %）处理。
     */
    public static int lensResolutionOrDefault(int percent) {
        return LENS_RESOLUTION_STEPS.contains(percent) ? percent : DEFAULT_LENS_RESOLUTION_PERCENT;
    }

    /** Stable lower-case name written to the config file. / 写入配置文件的稳定小写名称。 */
    public static String serializedName(ScopeMode mode) {
        return mode.name().toLowerCase(Locale.ROOT);
    }

    /** Unknown or missing names fall back to ZOOM_BLUR. / 未知或缺失的名称回退为全画面放大。 */
    public static ScopeMode parseMode(@Nullable String name) {
        if (name != null) {
            for (ScopeMode mode : ScopeMode.values()) {
                if (serializedName(mode).equals(name.trim().toLowerCase(Locale.ROOT))) {
                    return mode;
                }
            }
        }
        return ScopeMode.ZOOM_BLUR;
    }

    public static ScopeSettings fromJson(@Nullable JsonObject json) {
        if (json == null) {
            return DEFAULTS;
        }
        ScopeMode mode = ScopeMode.ZOOM_BLUR;
        JsonElement modeElement = json.get(MODE_KEY);
        if (modeElement instanceof JsonPrimitive primitive && primitive.isString()) {
            mode = parseMode(primitive.getAsString());
        }
        int percent = DEFAULT_SENSITIVITY_PERCENT;
        JsonElement percentElement = json.get(SENSITIVITY_KEY);
        if (percentElement instanceof JsonPrimitive primitive && primitive.isNumber()) {
            double value = primitive.getAsDouble();
            if (Double.isFinite(value)) {
                percent = (int) Math.round(Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value)));
            }
        }
        // Only a whole-number step is kept (75 or 75.0); a fraction, NaN or anything else becomes the default.
        // 只保留整数档位（75 或 75.0）；小数、NaN 或其他值都回退为默认值。
        int lensResolution = DEFAULT_LENS_RESOLUTION_PERCENT;
        JsonElement lensElement = json.get(LENS_RESOLUTION_KEY);
        if (lensElement instanceof JsonPrimitive primitive && primitive.isNumber()) {
            double value = primitive.getAsDouble();
            if (value == Math.rint(value) && Math.abs(value) <= Integer.MAX_VALUE) {
                lensResolution = (int) value;
            }
        }
        return new ScopeSettings(mode, percent, lensResolution);
    }

    /** Writes the three scope keys into {@code json}, leaving every other key in place. / 只写入三个开镜键。 */
    public void writeTo(JsonObject json) {
        json.addProperty(MODE_KEY, serializedName(mode));
        json.addProperty(SENSITIVITY_KEY, sensitivityPercent);
        json.addProperty(LENS_RESOLUTION_KEY, lensResolutionPercent);
    }
}
