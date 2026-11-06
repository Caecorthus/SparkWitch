package dev.caecorthus.sparkwitch.client.potiongunner;

import net.minecraft.util.math.Vec3d;

/**
 * Pure pose math for {@link PotionShellEntityRenderer}: the spin about the shell's own axis, the flight direction
 * rebuilt from vanilla projectile angles when the velocity is unusable, and vanilla's launch hide rule.
 * {@link PotionShellEntityRenderer} 的纯姿态数学：炮弹绕自身轴的自旋、速度不可用时由原版投射物角度还原的飞行方向，以及
 * 原版的出膛隐藏规则。
 */
public final class PotionShellModelPose {
    /** Spin speed: 30 degrees per tick, 600 degrees per second. / 自旋速度：每刻 30 度，即每秒 600 度。 */
    public static final float SPIN_DEGREES_PER_TICK = 30.0F;
    /**
     * Vanilla {@code FlyingItemEntityRenderer}: draw nothing while {@code age < 2} and the camera's focused entity is
     * within 3.5 blocks, so a projectile spawned at the eye never flashes across the shooter's screen.
     * 原版 {@code FlyingItemEntityRenderer}：{@code age < 2} 且镜头聚焦实体在 3.5 格内时不绘制，避免在眼部生成的投射物
     * 闪过射手屏幕。
     */
    public static final int LAUNCH_HIDE_TICKS = 2;
    public static final double LAUNCH_HIDE_DISTANCE_SQUARED = 12.25D;

    private PotionShellModelPose() {
    }

    /**
     * Roll about the model's +Z (its tail) after it faces the flight direction, wrapped to (-360, 0]. Negative is
     * clockwise seen from behind, a right-hand twist like a rifled round.
     * 模型朝向飞行方向后绕其 +Z（尾部）的横滚角，限制在 (-360, 0]。负值从后方看为顺时针，与右旋膛线弹相同。
     */
    public static float rollDegrees(int age, float tickDelta) {
        float turned = ((age + tickDelta) * SPIN_DEGREES_PER_TICK) % 360.0F;
        return turned == 0.0F ? 0.0F : -turned;
    }

    /** True while vanilla's launch hide rule applies. / 原版出膛隐藏规则生效时为真。 */
    public static boolean hiddenAtLaunch(int age, double focusedDistanceSquared) {
        return age < LAUNCH_HIDE_TICKS && focusedDistanceSquared < LAUNCH_HIDE_DISTANCE_SQUARED;
    }

    /**
     * Unit flight direction from vanilla projectile angles in degrees ({@code ProjectileEntity#updateRotation}: yaw =
     * atan2(vx, vz), pitch = atan2(vy, horizontal), positive up), the inverse of that convention.
     * 由原版投射物角度（度）还原的单位飞行方向（{@code ProjectileEntity#updateRotation}：偏航 = atan2(vx, vz)，俯仰 =
     * atan2(vy, 水平长度)，向上为正），即该约定的逆运算。
     */
    public static Vec3d directionFromProjectileAngles(float yawDegrees, float pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double horizontal = Math.cos(pitch);
        return new Vec3d(Math.sin(yaw) * horizontal, Math.sin(pitch), Math.cos(yaw) * horizontal);
    }
}
