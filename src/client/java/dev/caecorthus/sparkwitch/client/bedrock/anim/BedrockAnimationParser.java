package dev.caecorthus.sparkwitch.client.bedrock.anim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.caecorthus.sparkwitch.client.bedrock.BedrockModelException;
import dev.caecorthus.sparkwitch.client.bedrock.molang.MolangCompiler;
import dev.caecorthus.sparkwitch.client.bedrock.molang.MolangExpression;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a Bedrock {@code .animation.json}: every entry of {@code animations}, keyed by its full name, in file order.
 * 读取 Bedrock {@code .animation.json}：按文件顺序返回 {@code animations} 中的每一项，以完整名字为键。
 *
 * <p>Never trusts the file: counts are capped, numbers must be finite, and every error names the animation, bone,
 * channel and keyframe at fault. {@code sound_effects}, {@code particle_effects}, {@code timeline} and unknown keys
 * are ignored. A single number or Molang string applies to all three axes (for scale: uniform scale).
 * 从不信任文件内容：数量有上限、数字必须有限，每条错误都指出出错的动画、骨骼、通道与关键帧。忽略 sound_effects、
 * particle_effects、timeline 及未知键。单个数字或 Molang 字符串作用于全部三个轴（缩放即等比缩放）。</p>
 */
public final class BedrockAnimationParser {
    public static final int MAX_ANIMATIONS = 1024;
    public static final int MAX_BONES_PER_ANIMATION = 4096;
    public static final int MAX_KEYFRAMES_PER_CHANNEL = 8192;

    private static final String[] CHANNEL_KEYS = {"rotation", "position", "scale"};

    private BedrockAnimationParser() {
    }

    public static Map<String, BedrockAnimation> parse(JsonElement root) throws BedrockModelException {
        if (root == null || !root.isJsonObject()) {
            throw new BedrockModelException("animation file must be a JSON object");
        }
        JsonElement animations = root.getAsJsonObject().get("animations");
        if (animations == null) {
            throw new BedrockModelException("animation file has no \"animations\" object");
        }
        if (!animations.isJsonObject()) {
            throw new BedrockModelException("\"animations\" must be an object");
        }
        Map<String, MolangExpression> compiled = new HashMap<>();
        Map<String, BedrockAnimation> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : animations.getAsJsonObject().entrySet()) {
            if (result.size() >= MAX_ANIMATIONS) {
                throw new BedrockModelException("animation file has more than " + MAX_ANIMATIONS + " animations");
            }
            result.put(entry.getKey(), parseAnimation(entry.getKey(), entry.getValue(), compiled));
        }
        return Collections.unmodifiableMap(result);
    }

    private static BedrockAnimation parseAnimation(String name, JsonElement element,
                                                   Map<String, MolangExpression> compiled)
            throws BedrockModelException {
        String where = "animation '" + name + "'";
        if (!element.isJsonObject()) {
            throw new BedrockModelException(where + " must be an object");
        }
        JsonObject animation = element.getAsJsonObject();
        BedrockAnimation.LoopMode loopMode = parseLoop(animation.get("loop"), where);

        MolangExpression animTimeUpdate = optionalMolang(animation.get("anim_time_update"),
                where + " anim_time_update", compiled);
        MolangExpression blendWeight = optionalMolang(animation.get("blend_weight"),
                where + " blend_weight", compiled);
        if (blendWeight != null && blendWeight.isConstant() && blendWeight.evaluate(null) == 1.0) {
            blendWeight = null;
        }
        boolean overridePrevious = false;
        JsonElement override = animation.get("override_previous_animation");
        if (override != null && !override.isJsonNull()) {
            if (!(override.isJsonPrimitive() && override.getAsJsonPrimitive().isBoolean())) {
                throw new BedrockModelException(where + " override_previous_animation must be true or false");
            }
            overridePrevious = override.getAsBoolean();
        }

        List<String> boneNames = new ArrayList<>();
        List<KeyframeChannel> channels = new ArrayList<>();
        double lastKeyframe = 0.0;
        JsonElement bones = animation.get("bones");
        if (bones != null && !bones.isJsonNull()) {
            if (!bones.isJsonObject()) {
                throw new BedrockModelException(where + " \"bones\" must be an object");
            }
            for (Map.Entry<String, JsonElement> bone : bones.getAsJsonObject().entrySet()) {
                String boneWhere = where + ", bone '" + bone.getKey() + "'";
                if (!bone.getValue().isJsonObject()) {
                    throw new BedrockModelException(boneWhere + " must be an object");
                }
                if (boneNames.size() >= MAX_BONES_PER_ANIMATION) {
                    throw new BedrockModelException(where + " has more than " + MAX_BONES_PER_ANIMATION + " bones");
                }
                KeyframeChannel[] boneChannels = new KeyframeChannel[3];
                boolean any = false;
                for (int channel = 0; channel < 3; channel++) {
                    JsonElement value = bone.getValue().getAsJsonObject().get(CHANNEL_KEYS[channel]);
                    if (value == null || value.isJsonNull()) {
                        continue;
                    }
                    boneChannels[channel] = parseChannel(value, boneWhere + " " + CHANNEL_KEYS[channel], compiled);
                    lastKeyframe = Math.max(lastKeyframe, boneChannels[channel].lastTime());
                    any = true;
                }
                if (any) {
                    boneNames.add(bone.getKey());
                    Collections.addAll(channels, boneChannels);
                }
            }
        }

        double length = lastKeyframe;
        JsonElement lengthElement = animation.get("animation_length");
        if (lengthElement != null && !lengthElement.isJsonNull()) {
            length = number(lengthElement, where + " animation_length");
            if (length < 0.0) {
                throw new BedrockModelException(where + " animation_length must not be negative");
            }
        }
        return new BedrockAnimation(name, length, loopMode, animTimeUpdate, blendWeight, overridePrevious,
                boneNames.toArray(new String[0]), channels.toArray(new KeyframeChannel[0]));
    }

    private static BedrockAnimation.LoopMode parseLoop(@Nullable JsonElement loop, String where)
            throws BedrockModelException {
        if (loop == null || loop.isJsonNull()) {
            return BedrockAnimation.LoopMode.ONCE;
        }
        if (loop.isJsonPrimitive()) {
            JsonPrimitive primitive = loop.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean() ? BedrockAnimation.LoopMode.LOOP : BedrockAnimation.LoopMode.ONCE;
            }
            if (primitive.isString()) {
                switch (primitive.getAsString().trim().toLowerCase(Locale.ROOT)) {
                    case "hold_on_last_frame":
                        return BedrockAnimation.LoopMode.HOLD_ON_LAST_FRAME;
                    case "true":
                        return BedrockAnimation.LoopMode.LOOP;
                    case "false":
                        return BedrockAnimation.LoopMode.ONCE;
                    default:
                        break;
                }
            }
        }
        throw new BedrockModelException(where + " loop must be true, false or \"hold_on_last_frame\"");
    }

    /**
     * A channel: a value for all time ({@code [x, y, z]}, a number, a Molang string, or one keyframe object), or an
     * object of {@code "time": keyframe}.
     * 通道：恒定值（[x, y, z]、数字、Molang 字符串或单个关键帧对象），或 "时间": 关键帧 的对象。
     */
    private static KeyframeChannel parseChannel(JsonElement element, String where,
                                                Map<String, MolangExpression> compiled)
            throws BedrockModelException {
        List<Keyframe> keyframes = new ArrayList<>();
        if (element.isJsonObject() && !isKeyframeObject(element.getAsJsonObject())) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (keyframes.size() >= MAX_KEYFRAMES_PER_CHANNEL) {
                    throw new BedrockModelException(where + " has more than " + MAX_KEYFRAMES_PER_CHANNEL
                            + " keyframes");
                }
                double time = keyframeTime(entry.getKey(), where);
                keyframes.add(parseKeyframe(time, entry.getValue(), where + " keyframe " + entry.getKey(), compiled));
            }
            if (keyframes.isEmpty()) {
                throw new BedrockModelException(where + " has no keyframes");
            }
        } else {
            keyframes.add(parseKeyframe(0.0, element, where, compiled));
        }
        return buildChannel(keyframes);
    }

    private static boolean isKeyframeObject(JsonObject object) {
        return object.has("pre") || object.has("post") || object.has("vector") || object.has("lerp_mode");
    }

    private static double keyframeTime(String key, String where) throws BedrockModelException {
        double time;
        try {
            time = Double.parseDouble(key.trim());
        } catch (NumberFormatException e) {
            throw new BedrockModelException(where + " keyframe time '" + key + "' is not a number");
        }
        if (!Double.isFinite(time) || time < 0.0) {
            throw new BedrockModelException(where + " keyframe time '" + key + "' must be a number >= 0");
        }
        return time;
    }

    private static Keyframe parseKeyframe(double time, JsonElement element, String where,
                                          Map<String, MolangExpression> compiled) throws BedrockModelException {
        if (!element.isJsonObject()) {
            MolangExpression[] value = vector(element, where, compiled);
            return new Keyframe(time, value, value, KeyframeChannel.LINEAR);
        }
        JsonObject object = element.getAsJsonObject();
        byte lerpMode = KeyframeChannel.LINEAR;
        JsonElement lerp = object.get("lerp_mode");
        if (lerp != null && !lerp.isJsonNull()) {
            if (!(lerp.isJsonPrimitive() && lerp.getAsJsonPrimitive().isString())) {
                throw new BedrockModelException(where + " lerp_mode must be a string");
            }
            // Unknown modes fall back to linear rather than rejecting the file. / 未知模式回退为线性插值。
            lerpMode = switch (lerp.getAsString().trim().toLowerCase(Locale.ROOT)) {
                case "catmullrom" -> KeyframeChannel.CATMULLROM;
                case "step" -> KeyframeChannel.STEP;
                default -> KeyframeChannel.LINEAR;
            };
        }
        MolangExpression[] base = present(object, "vector") ? vector(object.get("vector"), where + " vector", compiled)
                : null;
        MolangExpression[] pre = present(object, "pre") ? vector(object.get("pre"), where + " pre", compiled) : base;
        MolangExpression[] post = present(object, "post") ? vector(object.get("post"), where + " post", compiled)
                : base;
        if (pre == null && post == null) {
            throw new BedrockModelException(where + " needs \"pre\", \"post\" or \"vector\"");
        }
        return new Keyframe(time, pre != null ? pre : post, post != null ? post : pre, lerpMode);
    }

    private static boolean present(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && !value.isJsonNull();
    }

    /** {@code [x, y, z]} (or {@code [v]}), a number, or a Molang string; the last two apply to all axes. */
    private static MolangExpression[] vector(JsonElement element, String where, Map<String, MolangExpression> compiled)
            throws BedrockModelException {
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (array.size() == 1) {
                MolangExpression value = component(array.get(0), where, compiled);
                return new MolangExpression[]{value, value, value};
            }
            if (array.size() != 3) {
                throw new BedrockModelException(where + " expected [x, y, z] but got " + array.size() + " values");
            }
            return new MolangExpression[]{
                    component(array.get(0), where + " x", compiled),
                    component(array.get(1), where + " y", compiled),
                    component(array.get(2), where + " z", compiled)
            };
        }
        MolangExpression value = component(element, where, compiled);
        return new MolangExpression[]{value, value, value};
    }

    private static MolangExpression component(JsonElement element, String where,
                                              Map<String, MolangExpression> compiled) throws BedrockModelException {
        if (element != null && element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                return MolangExpression.constant(finite(primitive.getAsDouble(), where));
            }
            if (primitive.isString()) {
                return molang(primitive.getAsString(), where, compiled);
            }
        }
        throw new BedrockModelException(where + " expected a number or a Molang string");
    }

    @Nullable
    private static MolangExpression optionalMolang(@Nullable JsonElement element, String where,
                                                   Map<String, MolangExpression> compiled)
            throws BedrockModelException {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return component(element, where, compiled);
    }

    /** Compiles once per distinct source in a file; expressions are immutable and shared. / 同一文件内相同源码只编译一次。 */
    private static MolangExpression molang(String source, String where, Map<String, MolangExpression> compiled)
            throws BedrockModelException {
        MolangExpression expression = compiled.get(source);
        if (expression == null) {
            try {
                expression = MolangCompiler.compile(source.isBlank() ? "0" : source);
            } catch (BedrockModelException e) {
                throw new BedrockModelException(where + ": " + e.getMessage(), e);
            }
            compiled.put(source, expression);
        }
        return expression;
    }

    private static double number(JsonElement element, String where) throws BedrockModelException {
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                return finite(primitive.getAsDouble(), where);
            }
            if (primitive.isString()) {
                try {
                    return finite(Double.parseDouble(primitive.getAsString().trim()), where);
                } catch (NumberFormatException ignored) {
                    // Falls through to the error below. / 落到下方的错误。
                }
            }
        }
        throw new BedrockModelException(where + " must be a number");
    }

    private static double finite(double value, String where) throws BedrockModelException {
        if (!Double.isFinite(value)) {
            throw new BedrockModelException(where + " must be a finite number");
        }
        return value;
    }

    private static KeyframeChannel buildChannel(List<Keyframe> parsed) {
        List<Keyframe> sorted = new ArrayList<>(parsed);
        sorted.sort(Comparator.comparingDouble(Keyframe::time));
        // Equal times ("0" and "0.0"): the one written last wins. / 时间相同时以文件中靠后的为准。
        List<Keyframe> keyframes = new ArrayList<>(sorted.size());
        for (Keyframe keyframe : sorted) {
            int last = keyframes.size() - 1;
            if (last >= 0 && keyframes.get(last).time() == keyframe.time()) {
                keyframes.set(last, keyframe);
            } else {
                keyframes.add(keyframe);
            }
        }

        int count = keyframes.size();
        double[] times = new double[count];
        double[] values = new double[count * 6];
        MolangExpression[] expressions = new MolangExpression[count * 6];
        byte[] lerpModes = new byte[count];
        boolean[] split = new boolean[count];
        boolean dynamic = false;
        for (int k = 0; k < count; k++) {
            Keyframe keyframe = keyframes.get(k);
            times[k] = keyframe.time();
            lerpModes[k] = keyframe.lerpMode();
            for (int axis = 0; axis < 3; axis++) {
                dynamic |= store(keyframe.pre()[axis], k * 6 + axis, values, expressions);
                dynamic |= store(keyframe.post()[axis], k * 6 + 3 + axis, values, expressions);
                MolangExpression pre = keyframe.pre()[axis];
                MolangExpression post = keyframe.post()[axis];
                boolean differs = pre.isConstant() && post.isConstant()
                        ? Double.compare(values[k * 6 + axis], values[k * 6 + 3 + axis]) != 0
                        : pre != post;
                split[k] |= differs;
            }
        }
        return new KeyframeChannel(times, values, dynamic ? expressions : null, lerpModes, split);
    }

    /** Folds a constant into {@code values}; keeps only non-constant expressions. Returns whether it kept one. */
    private static boolean store(MolangExpression expression, int index, double[] values,
                                 MolangExpression[] expressions) {
        if (expression.isConstant()) {
            values[index] = expression.evaluate(null);
            return false;
        }
        expressions[index] = expression;
        return true;
    }

    private record Keyframe(double time, MolangExpression[] pre, MolangExpression[] post, byte lerpMode) {
    }
}
