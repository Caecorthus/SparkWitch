package dev.caecorthus.sparkwitch.client.bear.ricochet;

import dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet.BearRicochetRules;

/**
 * Pure D5 daze curve (owner default 2026-11-05): a view jolt AWAY from the shot that snaps in, swings back once or
 * twice and settles by {@link BearRicochetRules#DAZE_TICKS}, plus a fading double-vision ghost. Deterministic in its
 * inputs and free of Minecraft state so it is unit-tested headless. Angles use Minecraft's convention: positive yaw
 * turns the view right, positive pitch tips it down; roll is the view-stack Z rotation (negative = left ear down).
 * Every amplitude scales linearly with vanilla's "Distortion Effects" (0 = no motion, no ghost); there is no flash.
 * 纯 D5 眩晕曲线（所有者默认，2026-11-05）：背离来枪方向的视角震动——瞬间甩开、回摆一两次、在
 * {@link BearRicochetRules#DAZE_TICKS} 内归位——外加渐隐的重影。只依赖输入、不读 Minecraft 状态，可无头单测。角度沿用
 * Minecraft 约定：偏航为正向右转，俯仰为正向下低头；翻滚是视角栈绕 Z 轴的旋转（负值 = 左耳向下）。所有幅度都与原版
 * “视觉扭曲效果”线性相乘（0 = 不动、无重影）；没有白闪。
 */
public final class RysTDazeCurve {
    /** Peak yaw/pitch jolt for a helmet-shell hit, degrees. / 头盔外壳命中的峰值偏航/俯仰震动（度）。 */
    public static final float JOLT_DEGREES = 9.0F;
    /** Peak roll for a pure side hit, degrees. / 纯侧面命中的峰值翻滚（度）。 */
    public static final float ROLL_DEGREES = 4.0F;
    /**
     * A visor-glass hit (D4) dazes harder: jolt and ghost separation times this. / 面罩玻璃命中（D4）更晕：震动与重影
     * 间距乘以此值。
     */
    public static final float GLASS_STRENGTH = 1.25F;
    /** Ticks for the snap to reach its peak. / 甩开达到峰值所需刻数。 */
    public static final float ATTACK_TICKS = 1.5F;
    /** Spring swings after the snap, across the rest of the daze. / 甩开之后、余下眩晕期间的回摆次数。 */
    public static final float WOBBLE_CYCLES = 2.0F;
    /** Exponential damping of the swings. / 回摆的指数阻尼。 */
    public static final float WOBBLE_DAMPING = 3.0F;
    /** Weight of a shot from above (pushes the head down) against a frontal one. / 来自上方（把头压低）相对正面的权重。 */
    public static final float VERTICAL_PUSH = 0.5F;
    /** A push shorter than this has no clear direction: a random one is used. / 推力短于此值即方向不明，改用随机方向。 */
    public static final float MIN_PUSH = 0.05F;
    /** Peak ghost separation, as a fraction of the screen height. / 重影峰值间距（屏幕高度的比例）。 */
    public static final float GHOST_OFFSET = 0.02F;
    /** Peak ghost opacity at distortion 1. / 扭曲为 1 时重影的峰值不透明度。 */
    public static final float GHOST_ALPHA = 0.45F;
    /** Ticks for the ghost to split off. / 重影分离所需刻数。 */
    public static final float GHOST_ATTACK_TICKS = 2.0F;
    /** The ghost's separation breathes this many times as the eyes refocus. / 双眼重新对焦时重影间距起伏的次数。 */
    public static final float GHOST_BREATHE_CYCLES = 1.5F;
    /** Vertical share of the mostly horizontal double vision. / 以水平为主的重影中的竖直分量。 */
    public static final float GHOST_SKEW = 0.35F;
    /** A yaw push weaker than this leaves the ghost's side to chance. / 偏航推力弱于此值时重影偏向随机决定。 */
    public static final float GHOST_SIDE_THRESHOLD = 0.2F;

    private RysTDazeCurve() {
    }

    /**
     * The once-per-daze direction: {@code (yaw, pitch)} is the unit view push in Minecraft degrees-sign convention,
     * {@code (ghostX, ghostY)} the unit screen direction of the ghost (GUI axes, y down), {@code strength} the
     * shell/glass multiplier. / 每次眩晕确定一次的方向：{@code (yaw, pitch)} 为 Minecraft 符号约定下的单位视角推力，
     * {@code (ghostX, ghostY)} 为重影在屏幕上的单位方向（GUI 坐标，y 向下），{@code strength} 为外壳/玻璃倍数。
     */
    public record Kick(float yaw, float pitch, float ghostX, float ghostY, float strength) {
    }

    /**
     * One frame of the daze. Ghost offsets are fractions of the screen height. / 眩晕的一帧。重影偏移为屏幕高度的比例。
     */
    public record Pose(float yaw, float pitch, float roll, float ghostX, float ghostY, float ghostAlpha) {
        public static final Pose NONE = new Pose(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

        public boolean hasJolt() {
            return yaw != 0.0F || pitch != 0.0F || roll != 0.0F;
        }
    }

    /**
     * The push for a shot whose origin lies along {@code (dx, dy, dz)} (world space, from the eyes; zero = unknown)
     * while the view faces {@code viewYaw}/{@code viewPitch}. The head is shoved away from the impact: a shot from the
     * right turns the view left, from the front tips it up, from behind tips it forward, from above pushes it down.
     * {@code randomAngle} (radians) picks the direction when the origin is unknown or ambiguous.
     * 来源位于 {@code (dx, dy, dz)}（世界坐标，自眼睛出发；零 = 未知）、视角朝向 {@code viewYaw}/{@code viewPitch} 时的推力。
     * 头被推离撞击点：右侧来枪视角左转，正面来枪仰头，背后来枪低头，上方来枪压低。来源未知或含糊时由
     * {@code randomAngle}（弧度）决定方向。
     */
    public static Kick kick(float dx, float dy, float dz, float viewYaw, float viewPitch, boolean glass,
                            float randomAngle) {
        float strength = glass ? GLASS_STRENGTH : 1.0F;
        double length = Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
        if (Double.isFinite(length) && length > 1.0E-4 && Float.isFinite(viewYaw) && Float.isFinite(viewPitch)) {
            double x = dx / length;
            double y = dy / length;
            double z = dz / length;
            double yaw = Math.toRadians(viewYaw);
            double pitch = Math.toRadians(viewPitch);
            // View basis: forward is the look vector, right is +90° yaw, up = right × forward.
            // 视角基：forward 为视线，right 为偏航 +90°，up = right × forward。
            double forwardX = -Math.sin(yaw) * Math.cos(pitch);
            double forwardY = -Math.sin(pitch);
            double forwardZ = Math.cos(yaw) * Math.cos(pitch);
            double rightX = -Math.cos(yaw);
            double rightZ = -Math.sin(yaw);
            double upX = -rightZ * forwardY;
            double upY = rightZ * forwardX - rightX * forwardZ;
            double upZ = rightX * forwardY;
            double lateral = x * rightX + z * rightZ;
            double frontal = x * forwardX + y * forwardY + z * forwardZ;
            double vertical = x * upX + y * upY + z * upZ;
            double pushYaw = -lateral;
            double pushPitch = -frontal + VERTICAL_PUSH * vertical;
            double push = Math.hypot(pushYaw, pushPitch);
            if (push >= MIN_PUSH) {
                return of((float) (pushYaw / push), (float) (pushPitch / push), strength, randomAngle);
            }
        }
        float angle = Float.isFinite(randomAngle) ? randomAngle : 0.0F;
        return of((float) Math.cos(angle), (float) Math.sin(angle), strength, angle);
    }

    private static Kick of(float yaw, float pitch, float strength, float randomAngle) {
        // The ghost lies where the image is thrown (opposite the view turn), mostly horizontal like real diplopia.
        // 重影落在画面被甩去的方向（与视角转向相反），像真实复视一样以水平为主。
        float side = Math.abs(yaw) >= GHOST_SIDE_THRESHOLD ? -Math.signum(yaw)
                : (Math.cos(randomAngle) >= 0.0 ? 1.0F : -1.0F);
        float ghostX = side;
        float ghostY = -pitch * GHOST_SKEW;
        float ghostLength = (float) Math.hypot(ghostX, ghostY);
        return new Kick(yaw, pitch, ghostX / ghostLength, ghostY / ghostLength, strength);
    }

    /**
     * The daze at {@code elapsedTicks} (ticks since receipt plus the partial tick) under {@code distortion} (vanilla
     * Distortion Effects, 0..1). {@link Pose#NONE} before 0, from {@link BearRicochetRules#DAZE_TICKS} on, or at zero
     * distortion. / {@code elapsedTicks}（收到后经过的刻数加部分刻）、扭曲 {@code distortion}（原版“视觉扭曲效果”，0..1）
     * 下的眩晕。0 之前、自 {@link BearRicochetRules#DAZE_TICKS} 起或扭曲为零时为 {@link Pose#NONE}。
     */
    public static Pose pose(Kick kick, float elapsedTicks, float distortion) {
        float scale = Float.isFinite(distortion) ? Math.min(1.0F, Math.max(0.0F, distortion)) : 0.0F;
        float duration = BearRicochetRules.DAZE_TICKS;
        if (kick == null || scale <= 0.0F || !(elapsedTicks >= 0.0F) || elapsedTicks >= duration) {
            return Pose.NONE;
        }
        float jolt = joltShape(elapsedTicks, duration) * scale * kick.strength();
        float progress = elapsedTicks / duration;
        float separation = ghostSeparation(elapsedTicks, duration) * GHOST_OFFSET * scale * kick.strength();
        float alpha = (float) (GHOST_ALPHA * scale * Math.pow(1.0F - progress, 1.5));
        return new Pose(
                kick.yaw() * JOLT_DEGREES * jolt,
                kick.pitch() * JOLT_DEGREES * jolt,
                kick.yaw() * ROLL_DEGREES * jolt,
                kick.ghostX() * separation,
                kick.ghostY() * separation,
                alpha);
    }

    /** Snap (ease-out to 1), then a damped spring that reaches 0 at the end. / 甩开（缓出到 1），随后在结束时归零的阻尼回摆。 */
    static float joltShape(float ticks, float duration) {
        if (ticks < ATTACK_TICKS) {
            float rise = ticks / ATTACK_TICKS;
            return rise * (2.0F - rise);
        }
        float settle = (ticks - ATTACK_TICKS) / (duration - ATTACK_TICKS);
        double envelope = (1.0 - settle) * (1.0 - settle) * Math.exp(-WOBBLE_DAMPING * settle);
        return (float) (envelope * Math.cos(2.0 * Math.PI * WOBBLE_CYCLES * settle));
    }

    /** Splits off, breathes, closes by the end (never negative). / 分离、起伏、在结束前合拢（从不为负）。 */
    static float ghostSeparation(float ticks, float duration) {
        float split = Math.min(1.0F, ticks / GHOST_ATTACK_TICKS);
        float progress = ticks / duration;
        double breathe = 0.75 + 0.25 * Math.cos(2.0 * Math.PI * GHOST_BREATHE_CYCLES * progress);
        return (float) (split * (2.0F - split) * (1.0 - progress) * breathe);
    }
}
