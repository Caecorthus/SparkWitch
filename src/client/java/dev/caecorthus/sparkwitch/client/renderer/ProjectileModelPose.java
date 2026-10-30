package dev.caecorthus.sparkwitch.client.renderer;

/**
 * Pure pose math for {@link ProjectileModels}: the yaw and pitch that turn a model's nose, authored pointing north
 * (-Z), onto a flight or chain direction. Yaw turns about +Y and pitch about +X, applied pitch first, so the model
 * never rolls; only a straight up or down direction leaves the yaw free, and it is then 0.
 * {@link ProjectileModels} 的纯姿态数学：把朝北（-Z）建模的模型头部转到飞行或链条方向所需的偏航与俯仰。偏航绕 +Y、
 * 俯仰绕 +X，先俯仰后偏航，因此模型从不横滚；只有竖直向上或向下时偏航不确定，此时取 0。
 */
public final class ProjectileModelPose {
    private static final double HORIZONTAL_EPSILON = 1.0E-6D;

    private ProjectileModelPose() {
    }

    /** Radians about +Y that turn north (0, 0, -1) toward {@code (dx, dz)}. / 绕 +Y 把北方 (0, 0, -1) 转向 {@code (dx, dz)} 的弧度。 */
    public static float yaw(double dx, double dz) {
        if (Math.abs(dx) < HORIZONTAL_EPSILON && Math.abs(dz) < HORIZONTAL_EPSILON) {
            return 0.0F;
        }
        return (float) Math.atan2(-dx, -dz);
    }

    /** Radians about +X that lift the nose to {@code dy}; positive is up. / 绕 +X 把头部抬到 {@code dy} 的弧度，正值向上。 */
    public static float pitch(double dx, double dy, double dz) {
        return (float) Math.atan2(dy, Math.sqrt(dx * dx + dz * dz));
    }

    /** True when the direction is too short to aim a model. / 方向过短、无法用于朝向时为真。 */
    public static boolean isDegenerate(double dx, double dy, double dz) {
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        return !(lengthSquared > HORIZONTAL_EPSILON);
    }
}
