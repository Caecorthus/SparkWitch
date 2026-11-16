package dev.caecorthus.sparkwitch.roles.civilian.bear.launcher;

import dev.caecorthus.sparkwitch.roles.civilian.bear.BearRules;
import net.minecraft.util.math.Vec3d;

/**
 * Pure, side-neutral ballistics of the 40 mm smoke round, shared by {@link SmokeRoundEntity} (both sides) and the
 * client's M2A1 sight, so the range scale in the sight and the flying round can never disagree. Reference: a 40 mm
 * low-velocity grenade leaves the M32A1 at about 76 m/s and falls under real gravity; one block is one metre and one
 * tick is 1/20 s, so the round starts at {@link #MUZZLE_SPEED} = 3.8 blocks per tick and loses {@link #GRAVITY} =
 * 9.81 / 400 blocks per tick each tick. {@link #DRAG} is a small per-tick velocity factor (about 4 % per second): it
 * keeps the last scale mark (200 m, about 2.9 s of flight) inside the 3 s backup fuse while a real-looking lob stays
 * (about 1.4 degrees of holdover at 25 m, 11 degrees at 200 m). In a train most shots are 5 to 40 blocks, where the
 * round flies almost flat and lands within about a block of the line of sight. Water is not modelled (no train has
 * any).
 * <p>
 * The step is the entity's own: the round spawns {@link #LAUNCH_BELOW_EYE} below the eye with the exact launch velocity
 * (no inherited shooter motion, no divergence); every tick it first moves by its velocity, then the velocity is
 * multiplied by {@link #DRAG} and loses {@link #GRAVITY} vertically ({@link #nextVelocity}). It moves on ages 1 to
 * {@link #MOVES_BEFORE_FUSE} and the backup fuse bursts it in place on its {@link BearRules#BACKUP_FUSE_TICKS}th tick.
 * 40 mm 烟雾榴弹的纯计算弹道（两端通用），由 {@link SmokeRoundEntity}（两端）与客户端 M2A1 瞄具共用，使瞄具内的射程刻度尺与飞行中的
 * 榴弹永远一致。参考：40 mm 低速榴弹以约 76 m/s 离开 M32A1，并受真实重力下坠；一格即一米、一刻即 1/20 秒，因此初速
 * {@link #MUZZLE_SPEED} = 每刻 3.8 格，每刻损失 {@link #GRAVITY} = 9.81 / 400 格/刻。{@link #DRAG} 为每刻的小幅速度系数（约每秒
 * 4%）：既让最远的刻度（200 米，约 2.9 秒飞行）落在 3 秒备用引信之内，又保留真实的抛物线（25 米约 1.4 度抬高量，200 米约 11
 * 度）。在列车中多数射击为 5 到 40 格，榴弹几乎平飞，落点距视线约一格以内。不模拟水（列车里没有水）。
 * <p>
 * 步进即实体自身的步进：榴弹在眼睛下方 {@link #LAUNCH_BELOW_EYE} 处以精确的发射速度生成（不继承射手运动，无散布）；每刻先按
 * 速度移动，再把速度乘以 {@link #DRAG} 并在竖直方向减去 {@link #GRAVITY}（{@link #nextVelocity}）。它在 age 1 到
 * {@link #MOVES_BEFORE_FUSE} 时移动，并在第 {@link BearRules#BACKUP_FUSE_TICKS} 刻由备用引信原地引爆。
 */
public final class M32Ballistics {
    /** 76 m/s. / 76 米/秒。 */
    public static final double MUZZLE_SPEED = 3.8;
    /** 9.81 m/s² in blocks per tick². / 以格/刻² 表示的 9.81 米/秒²。 */
    public static final double GRAVITY = 9.81 / 400.0;
    /** Velocity factor per tick (≈ 4 % per second). / 每刻速度系数（约每秒 4%）。 */
    public static final double DRAG = 0.998;
    /**
     * Vanilla {@code ThrownEntity(type, owner, world)} spawns at the owner's eye Y minus {@code 0.1F}.
     * 原版 {@code ThrownEntity(type, owner, world)} 在拥有者眼睛高度减 {@code 0.1F} 处生成。
     */
    public static final double LAUNCH_BELOW_EYE = 0.1F;
    /**
     * Moves before the backup fuse: {@code ServerWorld.tickEntity} increments {@code age} before {@code tick()}, and the
     * fuse bursts the round before it moves once {@code age >= BACKUP_FUSE_TICKS}.
     * 备用引信前的移动次数：{@code ServerWorld.tickEntity} 先递增 {@code age} 再调用 {@code tick()}，而一旦
     * {@code age >= BACKUP_FUSE_TICKS} 引信会在移动前引爆榴弹。
     */
    public static final int MOVES_BEFORE_FUSE = BearRules.BACKUP_FUSE_TICKS - 1;

    // ---- M2A1 range scale / M2A1 射程刻度尺 ----
    /**
     * The fixed range scale of the M2A1 (owner 2026-11-11: nothing to dial, the shooter holds over with the scale):
     * one mark every {@link #SCALE_STEP_METRES} from {@link #SCALE_MIN_METRES} to {@link #SCALE_MAX_METRES}. The sight
     * draws only the marks its window shows (25, 50 and 75 m at the default FOV).
     * M2A1 的固定射程刻度尺（所有者 2026-11-11：无需调节，射手按刻度尺抬高瞄准）：自 {@link #SCALE_MIN_METRES} 到
     * {@link #SCALE_MAX_METRES}，每 {@link #SCALE_STEP_METRES} 一条刻度。瞄具只画出窗口内看得到的刻度（默认视场角下为
     * 25、50、75 米）。
     */
    public static final int SCALE_MIN_METRES = 25;
    public static final int SCALE_MAX_METRES = 200;
    public static final int SCALE_STEP_METRES = 25;

    private M32Ballistics() {
    }

    /** One tick's velocity change after the move. / 移动之后一刻的速度变化。 */
    public static Vec3d nextVelocity(Vec3d velocity) {
        return new Vec3d(velocity.x * DRAG, velocity.y * DRAG - GRAVITY, velocity.z * DRAG);
    }

    /** Every scale mark in ascending order (8 of them). / 按升序排列的全部刻度（共 8 条）。 */
    public static int[] scaleMarks() {
        int count = (SCALE_MAX_METRES - SCALE_MIN_METRES) / SCALE_STEP_METRES + 1;
        int[] marks = new int[count];
        for (int i = 0; i < count; i++) {
            marks[i] = SCALE_MIN_METRES + i * SCALE_STEP_METRES;
        }
        return marks;
    }

    /**
     * Angle in radians between the bore ({@code pitchDegrees}, Minecraft convention: positive looks down) and the
     * eye-to-round direction at the moment the round is first {@code slantRange} blocks from the eye; positive means
     * the round is below the bore line there. The bore is the line of sight (the round always leaves along the look), so
     * this is how far below the screen centre a target at that slant range must sit to be hit: the M2A1 draws its
     * scale mark for that range exactly there. The path is the per-tick polyline
     * of the entity (straight segments between ticks, as its collision ray); the crossing is solved exactly on the
     * first segment whose end is at least the range away. NaN when the round never gets that far before the backup
     * fuse, or for a non-finite pitch or a non-positive range.
     * 枪膛方向（{@code pitchDegrees}，Minecraft 约定：正值朝下）与榴弹第一次距眼睛 {@code slantRange} 格时“眼睛到榴弹”方向之间的
     * 弧度夹角；正值表示此时榴弹位于枪膛线下方。枪膛线即视线（榴弹始终沿视线方向射出），因此这正是位于该斜距的目标必须处在
     * 屏幕中心下方多远才能被命中：M2A1 把该射程的刻度恰好画在那里。路径为实体的逐刻折线（两刻之间为直线段，与其
     * 碰撞射线一致）；在终点首次不小于该距离的线段上精确求解交点。若榴弹在备用引信前飞不到这么远，或俯仰角非有限、距离不为正，
     * 则返回 NaN。
     */
    public static double holdoverRadians(double pitchDegrees, double slantRange) {
        if (!(slantRange > 0.0) || !Double.isFinite(slantRange) || !Double.isFinite(pitchDegrees)) {
            return Double.NaN;
        }
        double elevation = Math.toRadians(-pitchDegrees);
        double x = 0.0;
        double y = -LAUNCH_BELOW_EYE;
        double vx = MUZZLE_SPEED * Math.cos(elevation);
        double vy = MUZZLE_SPEED * Math.sin(elevation);
        for (int tick = 0; tick < MOVES_BEFORE_FUSE; tick++) {
            double nextX = x + vx;
            double nextY = y + vy;
            if (Math.hypot(nextX, nextY) >= slantRange) {
                double dx = nextX - x;
                double dy = nextY - y;
                double a = dx * dx + dy * dy;
                double b = 2.0 * (x * dx + y * dy);
                double c = x * x + y * y - slantRange * slantRange;
                double t = (-b + Math.sqrt(Math.max(0.0, b * b - 4.0 * a * c))) / (2.0 * a);
                t = Math.min(1.0, Math.max(0.0, t));
                return elevation - Math.atan2(y + t * dy, x + t * dx);
            }
            x = nextX;
            y = nextY;
            vx *= DRAG;
            vy = vy * DRAG - GRAVITY;
        }
        return Double.NaN;
    }

    /**
     * Height relative to the eye after {@code horizontalDistance} blocks of horizontal travel along
     * {@code pitchDegrees}; NaN when the round never gets that far before the fuse.
     * 沿 {@code pitchDegrees} 水平飞行 {@code horizontalDistance} 格后相对眼睛的高度；在引信前飞不到该距离时为 NaN。
     */
    public static double heightAt(double pitchDegrees, double horizontalDistance) {
        if (!(horizontalDistance >= 0.0) || !Double.isFinite(pitchDegrees)) {
            return Double.NaN;
        }
        double elevation = Math.toRadians(-pitchDegrees);
        double x = 0.0;
        double y = -LAUNCH_BELOW_EYE;
        double vx = MUZZLE_SPEED * Math.cos(elevation);
        double vy = MUZZLE_SPEED * Math.sin(elevation);
        if (horizontalDistance == 0.0) {
            return y;
        }
        for (int tick = 0; tick < MOVES_BEFORE_FUSE; tick++) {
            double nextX = x + vx;
            double nextY = y + vy;
            if (nextX >= horizontalDistance) {
                double t = (horizontalDistance - x) / (nextX - x);
                return y + t * (nextY - y);
            }
            x = nextX;
            y = nextY;
            vx *= DRAG;
            vy = vy * DRAG - GRAVITY;
        }
        return Double.NaN;
    }

    /**
     * Ticks of flight until the round is first {@code slantRange} blocks from the eye when fired along
     * {@code pitchDegrees} (fractional, on the crossing segment); NaN when out of range.
     * 沿 {@code pitchDegrees} 发射后榴弹第一次距眼睛 {@code slantRange} 格所需的飞行刻数（在交点线段上取小数）；超出射程时为 NaN。
     */
    public static double flightTicks(double pitchDegrees, double slantRange) {
        if (!(slantRange > 0.0) || !Double.isFinite(slantRange) || !Double.isFinite(pitchDegrees)) {
            return Double.NaN;
        }
        double elevation = Math.toRadians(-pitchDegrees);
        double x = 0.0;
        double y = -LAUNCH_BELOW_EYE;
        double vx = MUZZLE_SPEED * Math.cos(elevation);
        double vy = MUZZLE_SPEED * Math.sin(elevation);
        for (int tick = 0; tick < MOVES_BEFORE_FUSE; tick++) {
            double nextX = x + vx;
            double nextY = y + vy;
            double end = Math.hypot(nextX, nextY);
            if (end >= slantRange) {
                double start = Math.hypot(x, y);
                return tick + (end == start ? 1.0 : Math.max(0.0, (slantRange - start) / (end - start)));
            }
            x = nextX;
            y = nextY;
            vx *= DRAG;
            vy = vy * DRAG - GRAVITY;
        }
        return Double.NaN;
    }
}
