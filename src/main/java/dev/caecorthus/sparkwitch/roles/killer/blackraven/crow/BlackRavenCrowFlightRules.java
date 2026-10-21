package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Pure crow flight math shared by the server validator and crow-client's prediction (no world access), modelled on
 * SparkStrength's drone ({@code DroneEntity.applyPilotStep}, {@code DroneFlight}): per-axis step caps, a refilling move
 * budget, and the play-area / world-border clamp. Block collision is the only world-dependent step and lives in
 * {@link BlackRavenCrowEntity}. Tune the speeds here.
 * 服务端校验与 crow-client 预测共用的乌鸦纯飞行计算（不访问世界），仿照 SparkStrength 无人机：分轴步长上限、按刻补充的移动预算，
 * 以及游戏区域 / 世界边界限制。方块碰撞是唯一依赖世界的步骤，位于 {@link BlackRavenCrowEntity}。速度在此调整。
 */
public final class BlackRavenCrowFlightRules {
    /** Horizontal cap: 1.2 blocks/tick = 24 blocks/s (owner Q8). / 水平上限：每刻 1.2 格 = 每秒 24 格（所有者 Q8）。 */
    public static final double HORIZONTAL_STEP = 1.2;
    /** Vertical cap in blocks/tick; easy to tune. / 垂直上限（格/刻），便于调整。 */
    public static final double VERTICAL_STEP = 0.8;
    /**
     * Budget cap in steps per axis group: absorbs packet jitter and short TPS dips, but flooding moves cannot raise
     * the sustained speed above the caps.
     * 每组轴的预算上限（步数）：吸收数据包抖动与短暂掉刻，但刷移动包无法让持续速度超过上限。
     */
    public static final int MOVE_BUDGET_STEPS = 3;
    /**
     * Moves applied as they arrive per server tick; later ones in the same tick merge into one pending latest pose that
     * the crow applies on its own server tick (a newer tick's move supersedes it), so a jitter or TPS-dip burst never
     * leaves the server crow a step behind.
     * 每个服务端刻按到达顺序立即应用的移动包数；同一刻内之后的移动包合并为一个待应用的最新位姿，由乌鸦在自身服务端刻中应用（更新刻的
     * 移动包会取代它），因此抖动或掉刻造成的突发不会让服务端乌鸦落后一步。
     */
    public static final int MAX_MOVES_PER_TICK = 2;
    /**
     * Server catch-up: one validated step may cover up to this many per-tick caps when the budget allows (a merged or
     * late pose), so the server never lags one step and bounces corrections; the refilling budget still bounds the
     * average speed to the caps (24 blocks/s). Client prediction keeps single-cap steps.
     * 服务端追赶：预算允许时，一次校验的位移最多可覆盖这么多个每刻上限（合并或迟到的位姿），使服务端不会落后一步并反复纠正；按刻补充的
     * 预算仍把平均速度限制在上限内（24 格/秒）。客户端预测仍使用单倍上限。
     */
    public static final int CATCH_UP_STEPS = 2;
    /** A result this close to the request is accepted without a correction. / 与请求相差不超过此值时不纠正。 */
    public static final double CORRECTION_EPSILON = 0.0625;
    /** Steps shorter than this are no movement (float noise). / 短于此值的位移视为不动（浮点噪声）。 */
    public static final double MIN_MOVE_SQUARED = 1.0E-12;

    private BlackRavenCrowFlightRules() {
    }

    /** Budget after {@code elapsedTicks} of world time, capped. / 经过若干世界刻后的预算（有上限）。 */
    public static double refillMoveBudget(double budget, double maxStep, long elapsedTicks) {
        double refilled = Math.max(0.0, budget) + Math.max(0L, elapsedTicks) * maxStep;
        return Math.min(refilled, maxStep * MOVE_BUDGET_STEPS);
    }

    /** Full budget for a fresh session. / 新会话的满预算。 */
    public static double fullBudget(double maxStep) {
        return maxStep * MOVE_BUDGET_STEPS;
    }

    /** Longest part accepted now: the axis cap, limited by the remaining budget. / 当前可接受的最长分量。 */
    public static double allowedStep(double maxStep, double budget) {
        return Math.max(0.0, Math.min(maxStep, budget));
    }

    /** Factor that shortens an XZ step to at most {@code allowed}. / 把水平位移缩短到不超过 allowed 的系数。 */
    public static double horizontalScale(double x, double z, double allowed) {
        if (!(allowed > 0.0)) {
            return 0.0;
        }
        double length = Math.sqrt(x * x + z * z);
        return length > allowed ? allowed / length : 1.0;
    }

    /** Vertical part clamped to {@code [-allowed, allowed]}. / 垂直分量限制在 [-allowed, allowed]。 */
    public static double clampVertical(double y, double allowed) {
        double limit = Math.max(0.0, allowed);
        return Math.clamp(y, -limit, limit);
    }

    /**
     * One requested step shortened to the caps and the remaining budgets (horizontal and vertical separately).
     * 按上限与剩余预算（水平、垂直分别计算）缩短一次请求的位移。
     */
    public static Vec3d limitStep(Vec3d requested, double horizontalBudget, double verticalBudget) {
        return limitStep(requested, horizontalBudget, verticalBudget, 1);
    }

    /**
     * {@link #limitStep(Vec3d, double, double)} with each cap multiplied by {@code steps} (at least 1): the server
     * validator passes {@link #CATCH_UP_STEPS}; the budgets still limit the step.
     * 每个上限乘以 {@code steps}（至少为 1）的 {@link #limitStep(Vec3d, double, double)}：服务端校验传入 {@link #CATCH_UP_STEPS}；
     * 位移仍受预算限制。
     */
    public static Vec3d limitStep(Vec3d requested, double horizontalBudget, double verticalBudget, int steps) {
        int multiple = Math.max(1, steps);
        double scale = horizontalScale(requested.x, requested.z,
                allowedStep(HORIZONTAL_STEP * multiple, horizontalBudget));
        double vertical = clampVertical(requested.y, allowedStep(VERTICAL_STEP * multiple, verticalBudget));
        return new Vec3d(requested.x * scale, vertical, requested.z * scale);
    }

    /**
     * Shortens one axis so the hull never crosses {@code [boundMin, boundMax]}. An axis already outside is never pushed
     * back (no teleport), only kept from going further out.
     * 缩短单轴位移，使机身不越过边界。已在边界外的轴不会被拉回（不瞬移），只是不能继续外移。
     */
    public static double clampAxis(double delta, double hullMin, double hullMax, double boundMin, double boundMax) {
        if (delta > 0.0) {
            return Math.max(0.0, Math.min(delta, boundMax - hullMax));
        }
        if (delta < 0.0) {
            return Math.min(0.0, Math.max(delta, boundMin - hullMin));
        }
        return 0.0;
    }

    /**
     * Flight bounds: Wathe's play area with x/z intersected with the world border, y between the play area's minY and
     * maxY. A null play area leaves only the world border (y unbounded).
     * 飞行边界：Wathe 游戏区域，x/z 与世界边界取交集，y 在游戏区域 minY 与 maxY 之间。游戏区域为 null 时只剩世界边界（y 不限）。
     */
    public static Box bounds(@Nullable Box playArea, double borderWest, double borderNorth, double borderEast,
                             double borderSouth) {
        if (playArea == null) {
            return new Box(borderWest, Double.NEGATIVE_INFINITY, borderNorth,
                    borderEast, Double.POSITIVE_INFINITY, borderSouth);
        }
        return new Box(Math.max(playArea.minX, borderWest), playArea.minY, Math.max(playArea.minZ, borderNorth),
                Math.min(playArea.maxX, borderEast), playArea.maxY, Math.min(playArea.maxZ, borderSouth));
    }

    /** Clamp a step so {@code hull} stays inside {@code bounds} on every axis. / 限制位移，使机身各轴都留在边界内。 */
    public static Vec3d clampToBounds(Vec3d movement, Box hull, Box bounds) {
        return new Vec3d(
                clampAxis(movement.x, hull.minX, hull.maxX, bounds.minX, bounds.maxX),
                clampAxis(movement.y, hull.minY, hull.maxY, bounds.minY, bounds.maxY),
                clampAxis(movement.z, hull.minZ, hull.maxZ, bounds.minZ, bounds.maxZ));
    }

    /** Whether the server result is close enough to the request. / 服务端结果是否足够接近请求。 */
    public static boolean accepted(Vec3d result, Vec3d requested) {
        return result.squaredDistanceTo(requested) <= CORRECTION_EPSILON * CORRECTION_EPSILON;
    }

    public static boolean isFinite(Vec3d value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
