package dev.caecorthus.sparkwitch.roles.witch.potiongunner;

/**
 * Pure, side-safe model of a fired shell's flight, used by the client scope's range ticks and by tests. It mirrors the
 * shell entity on top of vanilla {@code ThrownEntity}: the shell spawns {@link #LAUNCH_BELOW_EYE} below the eye with
 * the exact launch velocity, and every tick first moves by its velocity; on the first {@link #flatTicks()} ticks the
 * velocity then stays unchanged (flat flight, 20 moves to exactly 50.0 blocks), on every later tick it is multiplied by
 * {@link PotionGunnerRules#DRAG} and loses {@link PotionGunnerRules#GRAVITY} vertically, as in vanilla (which moves
 * before it applies drag and gravity, so the 21st move still uses the launch velocity). No wind, no divergence.
 * 炮弹飞行的纯计算模型（两端安全），供客户端瞄准镜射程刻度与测试使用。它与建立在原版 {@code ThrownEntity} 之上的炮弹
 * 实体一致：炮弹以精确的发射速度在眼睛下方 {@link #LAUNCH_BELOW_EYE} 处生成，每刻先按速度移动；前
 * {@link #flatTicks()} 刻速度保持不变（平飞，20 次移动恰好到 50.0 格），之后每刻速度乘以
 * {@link PotionGunnerRules#DRAG} 并在竖直方向减去 {@link PotionGunnerRules#GRAVITY}，与原版相同（原版先移动再施加
 * 阻力与重力，因此第 21 次移动仍使用发射速度）。无风、无散布。
 */
public final class PotionBallistics {
    /**
     * Vanilla {@code ThrownEntity(type, owner, world)} spawns at the owner's eye Y minus {@code 0.1F}.
     * 原版 {@code ThrownEntity(type, owner, world)} 在拥有者眼睛高度减 {@code 0.1F} 处生成。
     */
    public static final double LAUNCH_BELOW_EYE = 0.1F;

    private PotionBallistics() {
    }

    /**
     * The single flat-flight predicate of this model, on the start-of-tick path length (delegates to the shared rule;
     * the entity's own helper may replace it at integration).
     * 本模型唯一的平飞判定，参数为某刻开始时的路径长度（委托共用规则；整合时可换成实体自身的辅助方法）。
     */
    public static boolean isFlatTick(double pathLengthBeforeTick) {
        return PotionGunnerRules.isFlatTick(pathLengthBeforeTick);
    }

    /**
     * Leading flat ticks. The launch velocity is exact and constant while flat, so the start-of-tick path length is
     * snapped to {@code tick x MUZZLE_SPEED}, as the entity does: 20 at 2.5 blocks per tick.
     * 开头的平飞刻数。平飞期间发射速度精确且恒定，因此与实体一样把某刻开始时的路径长度对齐为
     * {@code 刻数 x MUZZLE_SPEED}：每刻 2.5 格时为 20。
     */
    public static int flatTicks() {
        int ticks = 0;
        while (ticks < PotionGunnerRules.SHELL_LIFETIME_TICKS
                && isFlatTick(ticks * (double) PotionGunnerRules.MUZZLE_SPEED)) {
            ticks++;
        }
        return ticks;
    }

    /**
     * Height of the shell relative to the shooter's eye when it has travelled {@code horizontalDistance} blocks
     * horizontally, fired along Minecraft {@code pitchDegrees} (positive looks down). Positions between two ticks
     * are joined by a straight segment, as the entity's own per-tick collision ray is. Returns NaN when the shell
     * never gets that far before it bursts ({@link PotionGunnerRules#SHELL_LIFETIME_TICKS}).
     * 以 Minecraft 俯仰角 {@code pitchDegrees}（正值朝下）发射后，炮弹水平飞行 {@code horizontalDistance} 格时相对于
     * 射手眼睛的高度。两刻之间的位置按直线段连接，与实体每刻的碰撞射线一致。若炮弹在空爆
     * （{@link PotionGunnerRules#SHELL_LIFETIME_TICKS}）前飞不到该距离，则返回 NaN。
     */
    public static double heightAt(double pitchDegrees, double horizontalDistance) {
        if (!(horizontalDistance >= 0.0) || !Double.isFinite(pitchDegrees)) {
            return Double.NaN;
        }
        double elevation = Math.toRadians(-pitchDegrees);
        double x = 0.0;
        double y = -LAUNCH_BELOW_EYE;
        double vx = PotionGunnerRules.MUZZLE_SPEED * Math.cos(elevation);
        double vy = PotionGunnerRules.MUZZLE_SPEED * Math.sin(elevation);
        if (horizontalDistance == 0.0) {
            return y;
        }
        int flatTicks = flatTicks();
        for (int tick = 0; tick < PotionGunnerRules.SHELL_LIFETIME_TICKS; tick++) {
            boolean flat = tick < flatTicks;
            double nextX = x + vx;
            double nextY = y + vy;
            if (nextX >= horizontalDistance) {
                double t = (horizontalDistance - x) / (nextX - x);
                return y + t * (nextY - y);
            }
            x = nextX;
            y = nextY;
            if (!flat) {
                vx *= PotionGunnerRules.DRAG;
                vy = vy * PotionGunnerRules.DRAG - PotionGunnerRules.GRAVITY;
            }
        }
        return Double.NaN;
    }

    /**
     * How far a level shot has fallen below the eye line at {@code horizontalDistance}, launch offset included.
     * 水平射击在 {@code horizontalDistance} 处低于视线的距离（含发射点偏移）。
     */
    public static double dropAt(double horizontalDistance) {
        return -heightAt(0.0, horizontalDistance);
    }

    /**
     * Angle in radians between the line of sight ({@code pitchDegrees}) and the eye-to-shell direction when the shell
     * has travelled {@code horizontalDistance}; positive means the shell is below the aim point. NaN when unreachable.
     * 视线（{@code pitchDegrees}）与炮弹水平飞行 {@code horizontalDistance} 时“眼睛到炮弹”方向之间的弧度夹角；
     * 正值表示炮弹位于瞄准点下方。不可达时为 NaN。
     */
    public static double angleBelowSight(double pitchDegrees, double horizontalDistance) {
        double height = heightAt(pitchDegrees, horizontalDistance);
        if (Double.isNaN(height) || horizontalDistance <= 0.0) {
            return Double.NaN;
        }
        return Math.toRadians(-pitchDegrees) - Math.atan2(height, horizontalDistance);
    }

    /**
     * Perspective projection of an angle below the view axis onto the screen: distance below the centre for a
     * vertical field of view {@code verticalFovDegrees} and half the screen height {@code halfHeight} (any unit).
     * NaN outside the representable range.
     * 视轴下方某角度在屏幕上的透视投影：在竖直视场角 {@code verticalFovDegrees}、半屏高 {@code halfHeight}（任意单位）
     * 下位于中心下方的距离。超出可表示范围时为 NaN。
     */
    public static double screenOffset(double angleBelowAxisRadians, double verticalFovDegrees, double halfHeight) {
        if (!Double.isFinite(angleBelowAxisRadians) || Math.abs(angleBelowAxisRadians) >= Math.PI / 2.0
                || !(verticalFovDegrees > 0.0) || verticalFovDegrees >= 180.0) {
            return Double.NaN;
        }
        return Math.tan(angleBelowAxisRadians) / Math.tan(Math.toRadians(verticalFovDegrees) / 2.0) * halfHeight;
    }
}
