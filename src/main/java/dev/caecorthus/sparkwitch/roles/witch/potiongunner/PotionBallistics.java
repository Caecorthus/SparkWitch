package dev.caecorthus.sparkwitch.roles.witch.potiongunner;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionShellFlight;

/**
 * Pure, side-safe model of a fired shell's flight, used by the client scope's range rows and by tests. It mirrors the
 * shell entity on top of vanilla {@code ThrownEntity}: the shell spawns {@link #LAUNCH_BELOW_EYE} below the eye with
 * the exact launch velocity, and every tick first moves by its velocity; on the first {@link #flatTicks()} ticks the
 * velocity then stays unchanged (flat flight, 20 moves to exactly 50.0 blocks), on every later tick it is multiplied by
 * {@link PotionGunnerRules#DRAG} and loses {@link PotionGunnerRules#GRAVITY} vertically, as in vanilla (which moves
 * before it applies drag and gravity, so the 21st move still uses the launch velocity). No wind, no divergence. A
 * shell that hits nothing bursts after {@link #MOVES_BEFORE_BURST} moves.
 * 炮弹飞行的纯计算模型（两端安全），供客户端瞄准镜射程横线与测试使用。它与建立在原版 {@code ThrownEntity} 之上的炮弹
 * 实体一致：炮弹以精确的发射速度在眼睛下方 {@link #LAUNCH_BELOW_EYE} 处生成，每刻先按速度移动；前
 * {@link #flatTicks()} 刻速度保持不变（平飞，20 次移动恰好到 50.0 格），之后每刻速度乘以
 * {@link PotionGunnerRules#DRAG} 并在竖直方向减去 {@link PotionGunnerRules#GRAVITY}，与原版相同（原版先移动再施加
 * 阻力与重力，因此第 21 次移动仍使用发射速度）。无风、无散布。未命中的炮弹在 {@link #MOVES_BEFORE_BURST} 次移动后空爆。
 */
public final class PotionBallistics {
    /**
     * Vanilla {@code ThrownEntity(type, owner, world)} spawns at the owner's eye Y minus {@code 0.1F}.
     * 原版 {@code ThrownEntity(type, owner, world)} 在拥有者眼睛高度减 {@code 0.1F} 处生成。
     */
    public static final double LAUNCH_BELOW_EYE = 0.1F;
    /**
     * Moves a shell makes before its mid-air burst. {@code ServerWorld.tickEntity} increments {@code age} before it
     * calls {@code tick()}, and the shell bursts once {@code age >= SHELL_LIFETIME_TICKS} before it moves, so it moves
     * on ages 1 to 99 and bursts in place on its 100th tick: one move fewer than the lifetime.
     * 炮弹空爆前的移动次数。{@code ServerWorld.tickEntity} 先递增 {@code age} 再调用 {@code tick()}，而炮弹在移动之前
     * 一旦 {@code age >= SHELL_LIFETIME_TICKS} 就爆炸，因此它在 age 1 到 99 时移动，并在第 100 刻原地爆炸：比寿命少一次移动。
     */
    public static final int MOVES_BEFORE_BURST = PotionGunnerRules.SHELL_LIFETIME_TICKS - 1;
    /** {@link #flatTicks()}, computed once (the scope re-solves its rows every frame). / 只计算一次的平飞刻数。 */
    private static final int FLAT_TICKS = countFlatTicks();

    private PotionBallistics() {
    }

    /**
     * The single flat-flight predicate of this model, on the start-of-tick path length. It is the shell entity's own
     * predicate, so the scope's range rows and the flying shell can never disagree.
     * 本模型唯一的平飞判定，参数为某刻开始时的路径长度。它就是炮弹实体自身的判定，因此射程横线与飞行中的炮弹永不不一致。
     */
    public static boolean isFlatTick(double pathLengthBeforeTick) {
        return PotionShellFlight.isFlatTick(pathLengthBeforeTick);
    }

    /**
     * Leading flat ticks. The launch velocity is exact and constant while flat, so the start-of-tick path length is
     * snapped to {@code tick x MUZZLE_SPEED}, as the entity does: 20 at 2.5 blocks per tick.
     * 开头的平飞刻数。平飞期间发射速度精确且恒定，因此与实体一样把某刻开始时的路径长度对齐为
     * {@code 刻数 x MUZZLE_SPEED}：每刻 2.5 格时为 20。
     */
    public static int flatTicks() {
        return FLAT_TICKS;
    }

    private static int countFlatTicks() {
        int ticks = 0;
        while (ticks < MOVES_BEFORE_BURST && isFlatTick(ticks * (double) PotionGunnerRules.MUZZLE_SPEED)) {
            ticks++;
        }
        return ticks;
    }

    /**
     * Height of the shell relative to the shooter's eye when it has travelled {@code horizontalDistance} blocks
     * horizontally, fired along Minecraft {@code pitchDegrees} (positive looks down). Positions between two ticks
     * are joined by a straight segment, as the entity's own per-tick collision ray is. Returns NaN when the shell
     * never gets that far in its {@link #MOVES_BEFORE_BURST} moves before the mid-air burst.
     * 以 Minecraft 俯仰角 {@code pitchDegrees}（正值朝下）发射后，炮弹水平飞行 {@code horizontalDistance} 格时相对于
     * 射手眼睛的高度。两刻之间的位置按直线段连接，与实体每刻的碰撞射线一致。若炮弹在空爆前的
     * {@link #MOVES_BEFORE_BURST} 次移动内飞不到该距离，则返回 NaN。
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
        for (int tick = 0; tick < MOVES_BEFORE_BURST; tick++) {
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
     * Angle in radians between the line of sight ({@code pitchDegrees}) and the eye-to-shell direction at the moment
     * the shell is first {@code lineOfSightRange} blocks from the eye (slant range, the distance a target at the aim
     * point would be at); positive means the shell is below the aim point. The scope's range rows are labelled in this
     * range and re-solved at the current pitch every frame. The path is the same per-tick polyline as
     * {@link #heightAt}: the first move whose end is at least the range away contains the crossing, solved exactly on
     * that segment (|start + t (end - start)| = range, the larger root, clamped to the segment). NaN when the shell
     * never gets that far from the eye in its {@link #MOVES_BEFORE_BURST} moves, or for a non-finite pitch or a
     * non-positive range.
     * 炮弹第一次距眼睛 {@code lineOfSightRange} 格（视线距离，即位于瞄准点的目标所在的距离）时，视线
     * （{@code pitchDegrees}）与“眼睛到炮弹”方向之间的弧度夹角；正值表示炮弹位于瞄准点下方。瞄准镜的射程横线按此距离
     * 标注，并在每帧按当前俯仰角重新求解。路径与 {@link #heightAt} 相同，为逐刻折线：终点距离首次不小于该距离的那一次
     * 移动包含交点，在该线段上精确求解（|起点 + t (终点 - 起点)| = 距离，取较大根并限制在线段内）。若炮弹在
     * {@link #MOVES_BEFORE_BURST} 次移动内离眼睛不到这么远，或俯仰角非有限、距离不为正，则返回 NaN。
     */
    public static double angleBelowSightAtRange(double pitchDegrees, double lineOfSightRange) {
        if (!(lineOfSightRange > 0.0) || !Double.isFinite(lineOfSightRange) || !Double.isFinite(pitchDegrees)) {
            return Double.NaN;
        }
        double elevation = Math.toRadians(-pitchDegrees);
        double x = 0.0;
        double y = -LAUNCH_BELOW_EYE;
        double vx = PotionGunnerRules.MUZZLE_SPEED * Math.cos(elevation);
        double vy = PotionGunnerRules.MUZZLE_SPEED * Math.sin(elevation);
        int flatTicks = flatTicks();
        for (int tick = 0; tick < MOVES_BEFORE_BURST; tick++) {
            double nextX = x + vx;
            double nextY = y + vy;
            if (Math.hypot(nextX, nextY) >= lineOfSightRange) {
                double dx = nextX - x;
                double dy = nextY - y;
                double a = dx * dx + dy * dy;
                double b = 2.0 * (x * dx + y * dy);
                double c = x * x + y * y - lineOfSightRange * lineOfSightRange;
                double t = (-b + Math.sqrt(Math.max(0.0, b * b - 4.0 * a * c))) / (2.0 * a);
                t = Math.min(1.0, Math.max(0.0, t));
                return elevation - Math.atan2(y + t * dy, x + t * dx);
            }
            x = nextX;
            y = nextY;
            if (tick >= flatTicks) {
                vx *= PotionGunnerRules.DRAG;
                vy = vy * PotionGunnerRules.DRAG - PotionGunnerRules.GRAVITY;
            }
        }
        return Double.NaN;
    }
}
