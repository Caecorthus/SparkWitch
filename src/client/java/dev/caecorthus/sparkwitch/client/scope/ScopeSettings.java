package dev.caecorthus.sparkwitch.client.scope;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The player's scope settings and their JSON codec (keys {@value #MODE_KEY} and {@value #SENSITIVITY_KEY} in
 * {@code config/sparkwitch-client.json}). Decoding is tolerant: a missing, mistyped or unknown value falls back to its
 * default, and the sensitivity is clamped to 10-200 % and snapped to 5 % steps. Writing only touches these two keys, so
 * other client settings stored in the same file later survive. Pure; unit-tested.
 * 玩家的开镜设置及其 JSON 编解码（{@code config/sparkwitch-client.json} 中的 {@value #MODE_KEY} 与
 * {@value #SENSITIVITY_KEY}）。解码是宽容的：缺失、类型错误或未知的值回退为默认值，灵敏度钳制到 10-200 % 并按 5 % 取整。
 * 写入只改动这两个键，日后存放在同一文件中的其他客户端设置得以保留。纯逻辑，有单元测试。
 */
public record ScopeSettings(ScopeMode mode, int sensitivityPercent) {
    public static final String MODE_KEY = "scopeMode";
    public static final String SENSITIVITY_KEY = "scopedSensitivityPercent";
    public static final int MIN_SENSITIVITY_PERCENT = 10;
    public static final int MAX_SENSITIVITY_PERCENT = 200;
    public static final int SENSITIVITY_STEP_PERCENT = 5;
    public static final int DEFAULT_SENSITIVITY_PERCENT = 100;
    public static final ScopeSettings DEFAULTS = new ScopeSettings(ScopeMode.ZOOM_BLUR, DEFAULT_SENSITIVITY_PERCENT);

    public ScopeSettings {
        mode = mode == null ? ScopeMode.ZOOM_BLUR : mode;
        sensitivityPercent = clampPercent(sensitivityPercent);
    }

    public ScopeSettings withMode(ScopeMode newMode) {
        return new ScopeSettings(newMode, sensitivityPercent);
    }

    public ScopeSettings withSensitivityPercent(int newPercent) {
        return new ScopeSettings(mode, newPercent);
    }

    /** Clamped to [10, 200] and rounded to the nearest 5 %. / 钳制到 [10, 200] 并取最近的 5 %。 */
    public static int clampPercent(int percent) {
        int clamped = Math.max(MIN_SENSITIVITY_PERCENT, Math.min(MAX_SENSITIVITY_PERCENT, percent));
        return Math.round(clamped / (float) SENSITIVITY_STEP_PERCENT) * SENSITIVITY_STEP_PERCENT;
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
        return new ScopeSettings(mode, percent);
    }

    /** Writes the two scope keys into {@code json}, leaving every other key in place. / 只写入两个开镜键。 */
    public void writeTo(JsonObject json) {
        json.addProperty(MODE_KEY, serializedName(mode));
        json.addProperty(SENSITIVITY_KEY, sensitivityPercent);
    }
}
