package dev.caecorthus.sparkwitch.client.blackraven.crow;

import net.minecraft.util.math.MathHelper;

/**
 * Pure animation of crow model B ("Blackcoat crow", owner pick 2026-10-21; spec
 * {@code docs/dev-harness/art/black-raven-crow/anim_B.md}). All values are absolute model-part rotations
 * {@code [pitch, yaw, roll]} in radians on top of the reset defaults (= glide). Poses: fast-flight flap (about 6 Hz at
 * 24 blocks/s, settling into the glide when slow), hover (airborne, under 1 block/s, 4 Hz), and perched idle (supported
 * and still). State changes blend over about 0.15 s; the flap phase accumulates and is never reset. Presentation only.
 * 乌鸦模型 B（"黑衣乌鸦"，所有者 2026-10-21 选定；规格见 anim_B.md）的纯动画。所有数值都是在重置后的默认值（= 滑翔）之上的
 * 绝对旋转 [pitch, yaw, roll]（弧度）。姿态：高速振翅（24 格/秒约 6 Hz，慢速时回到滑翔）、悬停（空中且低于 1 格/秒，4 Hz）、
 * 栖停（有支撑且静止）。状态切换约 0.15 秒过渡；振翅相位持续累加，从不重置。仅用于展示。
 */
public final class BlackRavenCrowAnimation {
    public static final float ROOT_PIVOT_Y = 19.5F;
    public static final float IDLE_ROOT_PIVOT_Y = 18.5931F;
    public static final float IDLE_ROOT_PIVOT_Z = 0.4F;
    /** Below this speed (blocks/s) the crow hovers or perches. / 低于此速度（格/秒）时悬停或栖停。 */
    public static final float HOVER_SPEED = 1.0F;
    public static final float HOVER_FLAP_HZ = 4.0F;
    /** Seconds for a full state blend. / 状态完全过渡所需秒数。 */
    public static final float BLEND_SECONDS = 0.15F;
    private static final float MAX_STEP_SECONDS = 0.25F;

    // Glide defaults (from model_B.json). / 滑翔默认值（取自 model_B.json）。
    static final float[] GLIDE_LEFT_WING = {0.0F, -0.08F, -0.12F};
    static final float[] GLIDE_LEFT_TIP = {0.0F, -0.20F, -0.06F};
    static final float GLIDE_LEG_PITCH = 1.35F;
    // Flap extremes, left side; the right side mirrors yaw and roll. / 振翅极值（左侧）；右侧 yaw 与 roll 取反。
    static final float[] UP_LEFT_WING = {0.0F, -0.10F, -1.05F};
    static final float[] UP_LEFT_TIP = {0.0F, -0.35F, 0.30F};
    static final float[] DOWN_LEFT_WING = {0.0F, -0.02F, 0.62F};
    static final float[] DOWN_LEFT_TIP = {0.0F, -0.06F, -0.22F};
    // Perched idle. / 栖停。
    static final float[] IDLE_LEFT_WING = {-1.6508F, -1.4708F, 0.0F};
    static final float[] IDLE_LEFT_TIP = {0.0F, 0.10F, 0.0F};

    private BlackRavenCrowAnimation() {
    }

    /** {@code s = clamp((v - 4) / 12, 0, 1)}, v in blocks/s. / 速度系数。 */
    public static float speedFactor(float blocksPerSecond) {
        return MathHelper.clamp((blocksPerSecond - 4.0F) / 12.0F, 0.0F, 1.0F);
    }

    /** {@code f = 3.5 + 2.5 s} Hz: 6 Hz at full speed. / 振翅频率：满速 6 Hz。 */
    public static float flapHz(float speedFactor) {
        return 3.5F + 2.5F * speedFactor;
    }

    /** {@code q = (1 - cos 2πφ) / 2}: 0 = wings up, 1 = wings down. / 0 为翅膀在上，1 为在下。 */
    public static float stroke(float phase) {
        return (1.0F - MathHelper.cos(MathHelper.TAU * phase)) * 0.5F;
    }

    /** Per-crow animation state, advanced by render time. / 每只乌鸦的动画状态，按渲染时间推进。 */
    public static final class State {
        private float phase;
        private float speedFactor;
        private float hover;
        private float perch;
        private double lastTime = Double.NaN;

        /**
         * Advances to render time {@code seconds} ({@code (age + tickDelta) / 20}). A backwards or first step only
         * resynchronises; a long gap is capped so a crow coming back into view does not spin.
         * 推进到渲染时间 seconds。时间倒退或首次调用只重新对齐；过长间隔会被截断，乌鸦重新进入视野时不会乱转。
         */
        public void advance(double seconds, float blocksPerSecond, boolean supported) {
            float targetSpeed = BlackRavenCrowAnimation.speedFactor(blocksPerSecond);
            boolean slow = blocksPerSecond < HOVER_SPEED;
            float targetPerch = slow && supported ? 1.0F : 0.0F;
            float targetHover = slow && !supported ? 1.0F : 0.0F;
            if (Double.isNaN(lastTime) || seconds < lastTime) {
                lastTime = seconds;
                speedFactor = targetSpeed;
                hover = targetHover;
                perch = targetPerch;
                return;
            }
            float dt = (float) Math.min(MAX_STEP_SECONDS, seconds - lastTime);
            lastTime = seconds;
            float blend = dt / BLEND_SECONDS;
            speedFactor = approach(speedFactor, targetSpeed, blend);
            hover = approach(hover, targetHover, blend);
            perch = approach(perch, targetPerch, blend);
            float hz = MathHelper.lerp(hover, flapHz(speedFactor), HOVER_FLAP_HZ);
            phase = (phase + hz * dt) % 1.0F;
        }

        public float phase() {
            return phase;
        }

        public float speedFactor() {
            return speedFactor;
        }

        public float hover() {
            return hover;
        }

        public float perch() {
            return perch;
        }

        private static float approach(float value, float target, float step) {
            return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
        }
    }

    /** Absolute part rotations for one frame. / 一帧的各部件绝对旋转。 */
    public record Pose(
            float rootPitch, float rootRoll, float rootPivotY, float rootPivotZ,
            float headPitch, float tailPitch, float legPitch,
            float[] leftWing, float[] leftTip, float[] rightWing, float[] rightTip
    ) {
    }

    /**
     * The frame pose. {@code lookPitchDegrees} is the crow's pitch (positive looks down = nose down);
     * {@code yawRateDegreesPerTick} banks the body in fast flight.
     * 本帧姿态。lookPitchDegrees 为乌鸦俯仰（为正表示向下看即低头）；yawRateDegreesPerTick 让高速飞行时机身侧倾。
     */
    public static Pose pose(State state, float lookPitchDegrees, float yawRateDegreesPerTick) {
        float s = state.speedFactor();
        float q = stroke(state.phase());
        // Flight (glide → flap by s). / 飞行（按 s 由滑翔过渡到振翅）。
        float[] flightWing = mix(GLIDE_LEFT_WING, flap(UP_LEFT_WING, DOWN_LEFT_WING, q), s);
        float[] flightTip = mix(GLIDE_LEFT_TIP, flap(UP_LEFT_TIP, DOWN_LEFT_TIP, q), s);
        float flightRootPitch = MathHelper.clamp(lookPitchDegrees * MathHelper.RADIANS_PER_DEGREE * 0.8F, -0.7F, 0.7F);
        float flightHeadPitch = -0.4F * flightRootPitch;
        float flightRoll = MathHelper.clamp(-yawRateDegreesPerTick * 0.015F, -0.45F, 0.45F) * s;
        float flightPivotY = ROOT_PIVOT_Y + 0.35F * s * (q - 0.5F);
        // Hover: body up, full flap. / 悬停：机身上仰，完整振翅。
        float[] hoverWing = flap(UP_LEFT_WING, DOWN_LEFT_WING, q);
        float[] hoverTip = flap(UP_LEFT_TIP, DOWN_LEFT_TIP, q);
        float h = state.hover();
        float[] wing = mix(flightWing, hoverWing, h);
        float[] tip = mix(flightTip, hoverTip, h);
        float rootPitch = MathHelper.lerp(h, flightRootPitch, -0.70F);
        float headPitch = MathHelper.lerp(h, flightHeadPitch, 0.55F);
        float tailPitch = MathHelper.lerp(h, 0.0F, -0.25F);
        float legPitch = MathHelper.lerp(h, GLIDE_LEG_PITCH, 0.25F);
        float roll = MathHelper.lerp(h, flightRoll, 0.0F);
        float pivotY = MathHelper.lerp(h, flightPivotY, ROOT_PIVOT_Y);
        // Perched idle, wings folded. / 栖停，翅膀收拢。
        float p = state.perch();
        wing = mix(wing, IDLE_LEFT_WING, p);
        tip = mix(tip, IDLE_LEFT_TIP, p);
        rootPitch = MathHelper.lerp(p, rootPitch, -0.60F);
        headPitch = MathHelper.lerp(p, headPitch, 0.55F);
        tailPitch = MathHelper.lerp(p, tailPitch, 0.10F);
        legPitch = MathHelper.lerp(p, legPitch, 0.60F);
        roll = MathHelper.lerp(p, roll, 0.0F);
        pivotY = MathHelper.lerp(p, pivotY, IDLE_ROOT_PIVOT_Y);
        float pivotZ = MathHelper.lerp(p, 0.0F, IDLE_ROOT_PIVOT_Z);
        return new Pose(rootPitch, roll, pivotY, pivotZ, headPitch, tailPitch, legPitch,
                wing, tip, mirror(wing), mirror(tip));
    }

    /** {@code up + q (down - up)} per component. / 逐分量插值。 */
    static float[] flap(float[] up, float[] down, float q) {
        return mix(up, down, q);
    }

    static float[] mix(float[] from, float[] to, float t) {
        return new float[] {
                MathHelper.lerp(t, from[0], to[0]),
                MathHelper.lerp(t, from[1], to[1]),
                MathHelper.lerp(t, from[2], to[2])};
    }

    /** Right-side rotation: pitch kept, yaw and roll negated. / 右侧旋转：pitch 不变，yaw 与 roll 取反。 */
    static float[] mirror(float[] left) {
        return new float[] {left[0], -left[1], -left[2]};
    }
}
