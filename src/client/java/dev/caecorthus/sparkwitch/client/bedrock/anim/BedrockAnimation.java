package dev.caecorthus.sparkwitch.client.bedrock.anim;

import dev.caecorthus.sparkwitch.client.bedrock.BedrockPose;
import dev.caecorthus.sparkwitch.client.bedrock.molang.MolangRuntime;

import java.util.function.ToIntFunction;

/**
 * One parsed Bedrock animation ({@code animations.<name>} of a {@code .animation.json}). Immutable; all playback state
 * (the time) belongs to the caller.
 * 一个已解析的 Bedrock 动画（{@code .animation.json} 中的 {@code animations.<name>}）。不可变；所有播放状态（时间）
 * 由调用方持有。
 */
public final class BedrockAnimation {
    // CONTRACT SKELETON (2026-11-02): the animation agent owns the fields and the constructor.

    public enum LoopMode {
        ONCE,
        LOOP,
        HOLD_ON_LAST_FRAME
    }

    public String name() {
        throw new UnsupportedOperationException("TODO");
    }

    /** Seconds; {@code animation_length}, else the last keyframe time. / 秒；取 animation_length，否则取最后关键帧时间。 */
    public float length() {
        throw new UnsupportedOperationException("TODO");
    }

    public LoopMode loopMode() {
        throw new UnsupportedOperationException("TODO");
    }

    /** A {@link LoopMode#ONCE} animation past its length draws nothing. / 单次动画超过时长后不再绘制。 */
    public boolean isFinished(double animTime) {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * Adds this animation's channels at {@code animTime} seconds into {@code pose}, scaled by {@code weight} (Bedrock
     * blending: rotation and position times weight, scale lerped from 1). Sets {@code anim_time} on the runtime first.
     * 把本动画在 {@code animTime} 秒时的通道叠加进 {@code pose}，按 {@code weight} 缩放（Bedrock 混合：旋转与位移乘以权重，
     * 缩放从 1 插值）。求值前先在运行时上设置 {@code anim_time}。
     */
    public void apply(double animTime, MolangRuntime runtime, ToIntFunction<String> boneIndex, BedrockPose pose,
                      float weight) {
        throw new UnsupportedOperationException("TODO");
    }
}
