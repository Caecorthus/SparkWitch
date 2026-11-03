package dev.caecorthus.sparkwitch.client.bedrock.anim;

import dev.caecorthus.sparkwitch.client.bedrock.BedrockPose;
import dev.caecorthus.sparkwitch.client.bedrock.molang.MolangExpression;
import dev.caecorthus.sparkwitch.client.bedrock.molang.MolangRuntime;
import org.jetbrains.annotations.Nullable;

import java.util.function.ToIntFunction;

/**
 * One parsed Bedrock animation ({@code animations.<name>} of a {@code .animation.json}). Immutable; all playback state
 * (the time) belongs to the caller.
 * 一个已解析的 Bedrock 动画（{@code .animation.json} 中的 {@code animations.<name>}）。不可变；所有播放状态（时间）
 * 由调用方持有。
 *
 * <p>Timing: {@link LoopMode#LOOP} wraps the time by {@link #length()}; a looping animation of length 0 (no keyframes
 * past 0, the usual Molang-only loop that Blockbench exports without {@code animation_length}) is not wrapped, so its
 * keyframes hold frame 0 while {@code q.anim_time} keeps running, as in Blockbench's preview and GeckoLib.
 * {@link LoopMode#HOLD_ON_LAST_FRAME} clamps to [0, length]; {@link LoopMode#ONCE} clamps too and applies nothing once
 * {@link #isFinished}. {@code anim_time_update}, when present, is evaluated with {@code anim_time} set to the caller's
 * time and its result is the time that is wrapped and sampled (the caller owns the clock, so there is no previous
 * frame to accumulate from; {@code q.anim_time + q.delta_time} therefore samples one frame ahead).
 * 计时：LOOP 按 {@link #length()} 取模；长度为 0 的循环动画（Blockbench 导出的仅含 Molang、没有 animation_length 的常见循环）
 * 不取模，关键帧停在第 0 帧而 {@code q.anim_time} 继续增长，与 Blockbench 预览和 GeckoLib 一致。HOLD_ON_LAST_FRAME 截断到
 * [0, length]；ONCE 同样截断，并在 {@link #isFinished} 后不再叠加任何内容。若存在 {@code anim_time_update}，则先把
 * {@code anim_time} 设为调用方时间再求值，结果即为取模与采样所用的时间（时间由调用方持有，没有上一帧可累加，因此
 * {@code q.anim_time + q.delta_time} 会提前一帧采样）。</p>
 */
public final class BedrockAnimation {
    static final int ROTATION = 0;
    static final int POSITION = 1;
    static final int SCALE = 2;

    private static final String ANIM_TIME = "anim_time";

    public enum LoopMode {
        ONCE,
        LOOP,
        HOLD_ON_LAST_FRAME
    }

    private final String name;
    private final double length;
    private final LoopMode loopMode;
    @Nullable
    private final MolangExpression animTimeUpdate;
    @Nullable
    private final MolangExpression blendWeight;
    private final boolean overridePrevious;
    private final String[] boneNames;
    /** [bone * 3 + ROTATION/POSITION/SCALE]; null where the bone lacks that channel. / 无该通道时为 null。 */
    private final KeyframeChannel[] channels;

    BedrockAnimation(String name, double length, LoopMode loopMode, @Nullable MolangExpression animTimeUpdate,
                     @Nullable MolangExpression blendWeight, boolean overridePrevious, String[] boneNames,
                     KeyframeChannel[] channels) {
        this.name = name;
        this.length = length;
        this.loopMode = loopMode;
        this.animTimeUpdate = animTimeUpdate;
        this.blendWeight = blendWeight;
        this.overridePrevious = overridePrevious;
        this.boneNames = boneNames;
        this.channels = channels;
    }

    public String name() {
        return name;
    }

    /** Seconds; {@code animation_length}, else the last keyframe time. / 秒；取 animation_length，否则取最后关键帧时间。 */
    public float length() {
        return (float) length;
    }

    public LoopMode loopMode() {
        return loopMode;
    }

    /** A {@link LoopMode#ONCE} animation past its length draws nothing. / 单次动画超过时长后不再绘制。 */
    public boolean isFinished(double animTime) {
        return loopMode == LoopMode.ONCE && animTime > length;
    }

    /** Bone names in file order (not resolved against any geometry). / 按文件顺序的骨骼名（未对照几何解析）。 */
    public int boneCount() {
        return boneNames.length;
    }

    public String boneName(int index) {
        return boneNames[index];
    }

    /**
     * Adds this animation's channels at {@code animTime} seconds into {@code pose}, scaled by {@code weight} (Bedrock
     * blending: rotation and position times weight, scale lerped from 1). Sets {@code anim_time} on the runtime first.
     * 把本动画在 {@code animTime} 秒时的通道叠加进 {@code pose}，按 {@code weight} 缩放（Bedrock 混合：旋转与位移乘以权重，
     * 缩放从 1 插值）。求值前先在运行时上设置 {@code anim_time}。
     */
    public void apply(double animTime, MolangRuntime runtime, ToIntFunction<String> boneIndex, BedrockPose pose,
                      float weight) {
        double time = Double.isFinite(animTime) ? animTime : 0.0;
        if (isFinished(time) || weight == 0.0F || !Float.isFinite(weight)) {
            return;
        }
        runtime.setQuery(ANIM_TIME, time);
        if (animTimeUpdate != null) {
            double updated = animTimeUpdate.evaluate(runtime);
            if (Double.isFinite(updated)) {
                time = updated;
            }
        }
        time = wrap(time);
        runtime.setQuery(ANIM_TIME, time);

        double effectiveWeight = weight;
        if (blendWeight != null) {
            effectiveWeight *= blendWeight.evaluate(runtime);
        }
        if (effectiveWeight == 0.0 || !Double.isFinite(effectiveWeight)) {
            return;
        }

        for (int bone = 0; bone < boneNames.length; bone++) {
            int target = boneIndex.applyAsInt(boneNames[bone]);
            if (target < 0) {
                continue;
            }
            if (overridePrevious) {
                pose.resetBone(target);
            }
            KeyframeChannel rotation = channels[bone * 3 + ROTATION];
            if (rotation != null) {
                int segment = rotation.locate(time);
                pose.addRotation(target,
                        (float) (rotation.sample(segment, time, 0, runtime) * effectiveWeight),
                        (float) (rotation.sample(segment, time, 1, runtime) * effectiveWeight),
                        (float) (rotation.sample(segment, time, 2, runtime) * effectiveWeight));
            }
            KeyframeChannel position = channels[bone * 3 + POSITION];
            if (position != null) {
                int segment = position.locate(time);
                pose.addPosition(target,
                        (float) (position.sample(segment, time, 0, runtime) * effectiveWeight),
                        (float) (position.sample(segment, time, 1, runtime) * effectiveWeight),
                        (float) (position.sample(segment, time, 2, runtime) * effectiveWeight));
            }
            KeyframeChannel scale = channels[bone * 3 + SCALE];
            if (scale != null) {
                int segment = scale.locate(time);
                pose.multiplyScale(target,
                        (float) (1.0 + (scale.sample(segment, time, 0, runtime) - 1.0) * effectiveWeight),
                        (float) (1.0 + (scale.sample(segment, time, 1, runtime) - 1.0) * effectiveWeight),
                        (float) (1.0 + (scale.sample(segment, time, 2, runtime) - 1.0) * effectiveWeight));
            }
        }
    }

    private double wrap(double time) {
        if (loopMode == LoopMode.LOOP) {
            if (length <= 0.0) {
                return time;
            }
            double wrapped = time - Math.floor(time / length) * length;
            return wrapped >= length ? 0.0 : wrapped;
        }
        return Math.max(0.0, Math.min(length, time));
    }

    @Override
    public String toString() {
        return "BedrockAnimation[" + name + ", " + loopMode + ", " + length + "s, " + boneNames.length + " bones]";
    }
}
