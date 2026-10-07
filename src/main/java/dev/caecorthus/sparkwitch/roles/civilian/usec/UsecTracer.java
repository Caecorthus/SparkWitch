package dev.caecorthus.sparkwitch.roles.civilian.usec;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stable contract: the server's USEC bullet tracer against blocks (D4, Q3+), pure apart from its {@link BlockProbe}.
 * It walks the trajectory in straight steps of at most {@link #STEP} blocks flown along the aim. FMJ flies straight to
 * {@link UsecBallistics#fmjRange} and stops at the first block. AP spends energy per block flown and per penetrated block
 * ({@link UsecBallistics}); each step bends down by that step's sink ({@link UsecBallistics#sinkAfter}), so the path is
 * a continuous polyline whose unobstructed drop equals {@link UsecBallistics#apDropAt}. On a block, AP pays
 * {@link UsecRules#AP_PENETRATION_COST}, enters it, leaves it at its shape's exit along the current step direction (a
 * block counts once however thin) and flies on; it stops at a map wall, or inside the block when the energy runs out.
 * Players, devices and puppets are the caller's concern (they cut the returned path); this class never touches them.
 * Drop is world-vertical and accrues per block flown along the aim, the same axis the scope's holdover marks use.
 * 稳定契约：服务端 USEC 子弹对方块的弹道追踪（D4、Q3+），除 {@link BlockProbe} 外为纯逻辑。沿瞄准方向按每步最多
 * {@link #STEP} 格推进。FMJ 直线飞到 {@link UsecBallistics#fmjRange}，遇到第一个方块即停。AP 按飞行格数与穿透方块数消耗
 * 能量（{@link UsecBallistics}）；每一步按该步的下沉量（{@link UsecBallistics#sinkAfter}）向下弯折，因此路径是连续折线，
 * 无障碍时的下沉与 {@link UsecBallistics#apDropAt} 一致。遇到方块时 AP 支付 {@link UsecRules#AP_PENETRATION_COST}，进入
 * 方块，沿当前步方向从其形状出口离开（再薄的方块也只算一次）并继续飞行；遇到地图墙即停，能量在方块内耗尽时停在方块内。
 * 玩家、设备与皮套由调用方处理（截断返回的路径）；本类从不触碰它们。下沉沿世界竖直方向，按沿瞄准方向飞过的格数累计，
 * 与瞄准镜抬枪刻度使用同一轴。
 */
public final class UsecTracer {
    /** Longest step, in blocks flown along the aim. / 最长步长（沿瞄准方向飞行的格数）。 */
    public static final double STEP = 1.0;
    /** Nudge past a cell face when resuming after a block. / 越过方块格面恢复追踪时的微小偏移。 */
    static final double EPSILON = 1.0E-4;
    /** Remaining flight below this ends the trace. / 剩余飞行低于此值时结束追踪。 */
    static final double MIN_FLIGHT = 1.0E-6;
    /** Hard cap on steps (260 blocks of AP flight plus penetrations need far fewer). / 步数硬上限。 */
    static final int MAX_STEPS = 4096;
    /** Re-casts allowed inside one step when the ray re-reports a block already passed. / 单步内允许的重投次数。 */
    private static final int MAX_RESUMES = 4;

    private UsecTracer() {
    }

    /**
     * The block world as the tracer sees it. The server implementation is {@link UsecTracerWorldProbe}; tests use a fake.
     * 追踪器眼中的方块世界。服务端实现为 {@link UsecTracerWorldProbe}；测试使用伪造实现。
     */
    public interface BlockProbe {
        /**
         * Whether every chunk the segment touches is loaded; the tracer stops before the first segment that is not and
         * never makes the world load one. / 线段经过的区块是否全部已加载；追踪器在第一个未加载线段之前停止，绝不触发区块加载。
         */
        boolean isLoaded(Vec3d from, Vec3d to);

        /**
         * The first block whose ray shape the segment meets (COLLIDER shapes, no fluids, closed doors solid), or null.
         * 线段遇到的第一个方块（COLLIDER 形状、忽略流体、关闭的门为实心）；没有时为 null。
         */
        @Nullable
        BlockHit raycast(Vec3d from, Vec3d to);

        /** A map wall no round can pierce. / 任何子弹都无法穿透的地图墙。 */
        boolean isImpenetrable(BlockPos pos);

        /**
         * Where a straight line that enters the block at {@code entry} along the unit {@code direction} last leaves the
         * block's ray shape inside its cell; {@code entry} when it cannot tell.
         * 沿单位方向 {@code direction} 在 {@code entry} 进入方块的直线，在该方块格内最后离开其射线形状之处；无法判断时返回
         * {@code entry}。
         */
        Vec3d exitPoint(BlockPos pos, Vec3d entry, Vec3d direction);
    }

    /** A ray meeting a block. / 射线与方块的相遇。 */
    public record BlockHit(BlockPos pos, Vec3d point) {
        public BlockHit {
            pos = pos.toImmutable();
        }
    }

    /** Why the bullet's flight ended. / 子弹飞行结束的原因。 */
    public enum Stop {
        /** FMJ reached its range (or the step cap). / FMJ 达到射程（或步数上限）。 */
        RANGE,
        /** FMJ met a block. / FMJ 撞上方块。 */
        BLOCK,
        /** A vanilla barrier or Wathe barrier panel. / 原版屏障或 Wathe 屏障板。 */
        MAP_WALL,
        /** AP energy ran out, in the air or inside a block. / AP 能量耗尽（空中或方块内）。 */
        EXHAUSTED,
        /** The next segment touches an unloaded chunk. / 下一段经过未加载区块。 */
        UNLOADED
    }

    /** A block the bullet passed through, with the path distance at its entry. / 子弹穿过的方块及其入射处的路径距离。 */
    public record Penetration(BlockPos pos, Vec3d entry, Vec3d exit, double distance) {
        public Penetration {
            pos = pos.toImmutable();
        }
    }

    /** The block the bullet ended in or against (not passed). / 子弹停在其上或其中（未穿过）的方块。 */
    public record BlockStop(BlockPos pos, Vec3d point, double distance) {
        public BlockStop {
            pos = pos.toImmutable();
        }
    }

    /**
     * One traced flight: the path (vertices at every bend, block entry and exit), the penetrated blocks in flight
     * order, the block the flight ended at (null in the air, at range or before an unloaded chunk), and why it ended.
     * 一次追踪结果：路径（每个弯折、方块入口与出口都是顶点）、按飞行顺序排列的被穿透方块、飞行终止的方块（空中、射程尽头或
     * 未加载区块前为 null），以及终止原因。
     */
    public record Trace(UsecShotPath path, List<Penetration> penetrations, @Nullable BlockStop blockStop, Stop stop) {
        public Trace {
            penetrations = List.copyOf(penetrations);
        }
    }

    /**
     * Traces one round from {@code start} along {@code direction} (need not be normalized; a zero direction traces
     * nothing). {@code marksman} is the SparkTraits Marksman multiplier (sanitized by {@link UsecBallistics}).
     * 从 {@code start} 沿 {@code direction}（无需归一化；零向量不追踪）追踪一发子弹。{@code marksman} 为 SparkTraits 精确枪手倍率
     * （由 {@link UsecBallistics} 清理）。
     */
    public static Trace trace(Vec3d start, Vec3d direction, UsecAmmoType ammo, double marksman, BlockProbe probe) {
        PathBuilder path = new PathBuilder(start);
        List<Penetration> penetrations = new ArrayList<>();
        double aimLength = direction == null ? 0.0 : direction.length();
        if (!(aimLength > 1.0E-7) || ammo == null || probe == null) {
            return new Trace(path.build(), penetrations, null, Stop.RANGE);
        }
        Vec3d aim = direction.multiply(1.0 / aimLength);
        boolean piercing = ammo.penetrates();
        double cost = piercing ? UsecBallistics.flightCostPerBlock(marksman) : 0.0;
        double range = UsecBallistics.fmjRange(marksman);
        Set<BlockPos> passed = new HashSet<>();
        Vec3d position = start;
        double flown = 0.0;
        double spent = 0.0;
        BlockStop blockStop = null;
        Stop stop = Stop.RANGE;
        for (int step = 0; step < MAX_STEPS; step++) {
            double remaining = piercing ? UsecBallistics.remainingFlight(spent, cost) : range - flown;
            if (!(remaining > MIN_FLIGHT)) {
                stop = piercing ? Stop.EXHAUSTED : Stop.RANGE;
                break;
            }
            double blocks = Math.min(STEP, remaining);
            double sink = piercing ? UsecBallistics.sinkAfter(spent, blocks, cost) : 0.0;
            Vec3d delta = aim.multiply(blocks).add(0.0, -sink, 0.0);
            Vec3d next = position.add(delta);
            if (!probe.isLoaded(position, next)) {
                stop = Stop.UNLOADED;
                break;
            }
            double stepLength = delta.length();
            Vec3d unit = delta.multiply(1.0 / stepLength);
            BlockHit hit = firstNewBlock(probe, position, next, unit, stepLength, passed);
            if (hit == null) {
                flown += blocks;
                spent = UsecBallistics.spentAfterFlight(spent, blocks, cost);
                path.advance(next, sink == 0.0);
                position = next;
                continue;
            }
            double fraction = Math.max(0.0, Math.min(1.0, (hit.point().subtract(position)).dotProduct(unit) / stepLength));
            flown += blocks * fraction;
            spent = UsecBallistics.spentAfterFlight(spent, blocks * fraction, cost);
            position = position.add(unit.multiply(stepLength * fraction));
            path.pin(position);
            double atBlock = path.length();
            if (!piercing) {
                blockStop = new BlockStop(hit.pos(), position, atBlock);
                stop = Stop.BLOCK;
                break;
            }
            if (probe.isImpenetrable(hit.pos())) {
                blockStop = new BlockStop(hit.pos(), position, atBlock);
                stop = Stop.MAP_WALL;
                break;
            }
            double afterPenetration = UsecBallistics.spentAfterPenetration(spent);
            Vec3d exit = probe.exitPoint(hit.pos(), position, unit);
            double thickness = exit == null ? 0.0 : Math.max(0.0, exit.subtract(position).dotProduct(unit));
            // Blocks flown along the aim while crossing the block (the step's chord scaled back to the aim axis).
            // 穿过方块期间沿瞄准方向飞过的格数（按该步弦长换算回瞄准轴）。
            double crossing = thickness * (blocks / stepLength);
            if (UsecBallistics.isExhausted(afterPenetration)
                    || UsecBallistics.remainingFlight(afterPenetration, cost) < crossing) {
                blockStop = new BlockStop(hit.pos(), position, atBlock);
                stop = Stop.EXHAUSTED;
                break;
            }
            Vec3d out = position.add(unit.multiply(thickness));
            penetrations.add(new Penetration(hit.pos(), position, out, atBlock));
            passed.add(hit.pos());
            flown += crossing;
            spent = UsecBallistics.spentAfterFlight(afterPenetration, crossing, cost);
            position = out;
            path.pin(position);
        }
        return new Trace(path.build(), penetrations, blockStop, stop);
    }

    /**
     * Where a line through {@code inside} along the unit {@code direction} leaves the unit cell of {@code pos}.
     * 经过 {@code inside}、沿单位方向 {@code direction} 的直线离开 {@code pos} 单位格之处。
     */
    public static Vec3d cellExit(BlockPos pos, Vec3d inside, Vec3d direction) {
        double t = Double.POSITIVE_INFINITY;
        t = Math.min(t, axisExit(inside.x, direction.x, pos.getX()));
        t = Math.min(t, axisExit(inside.y, direction.y, pos.getY()));
        t = Math.min(t, axisExit(inside.z, direction.z, pos.getZ()));
        if (!Double.isFinite(t)) {
            return inside;
        }
        return inside.add(direction.multiply(Math.max(0.0, t)));
    }

    private static double axisExit(double value, double direction, int cell) {
        if (direction > 1.0E-12) {
            return (cell + 1 - value) / direction;
        }
        if (direction < -1.0E-12) {
            return (cell - value) / direction;
        }
        return Double.POSITIVE_INFINITY;
    }

    /**
     * The first block on {@code from → to} that the bullet has not already passed through. A ray that re-reports a
     * passed block (float noise at its exit face) is re-cast from just past that block's cell.
     * {@code from → to} 上子弹尚未穿过的第一个方块。射线若再次报告已穿过的方块（出口面处的浮点误差），则从该方块格之后重新投射。
     */
    private static @Nullable BlockHit firstNewBlock(BlockProbe probe, Vec3d from, Vec3d to, Vec3d unit,
                                                    double stepLength, Set<BlockPos> passed) {
        Vec3d cursor = from;
        for (int resume = 0; resume <= MAX_RESUMES; resume++) {
            BlockHit hit = probe.raycast(cursor, to);
            if (hit == null) {
                return null;
            }
            if (!passed.contains(hit.pos())) {
                return hit;
            }
            cursor = cellExit(hit.pos(), hit.point(), unit).add(unit.multiply(EPSILON));
            if (cursor.subtract(from).dotProduct(unit) >= stepLength) {
                return null;
            }
        }
        return null;
    }

    /**
     * Builds the polyline: a straight (no-sink) step extends the previous straight step instead of adding a vertex,
     * unless that vertex is pinned (the start, a block entry or exit).
     * 构建折线：直线（无下沉）步延长上一段直线步而不新增顶点，除非该顶点被固定（起点、方块入口或出口）。
     */
    private static final class PathBuilder {
        private final List<Vec3d> points = new ArrayList<>();
        private boolean lastPinned = true;
        private boolean lastStraight;
        private double length;

        PathBuilder(Vec3d start) {
            points.add(start);
        }

        void advance(Vec3d next, boolean straight) {
            Vec3d last = points.getLast();
            length += last.distanceTo(next);
            if (straight && lastStraight && !lastPinned) {
                points.set(points.size() - 1, next);
            } else {
                points.add(next);
            }
            lastPinned = false;
            lastStraight = straight;
        }

        void pin(Vec3d point) {
            Vec3d last = points.getLast();
            if (last.squaredDistanceTo(point) > 1.0E-18) {
                length += last.distanceTo(point);
                points.add(point);
            }
            lastPinned = true;
            lastStraight = false;
        }

        double length() {
            return length;
        }

        UsecShotPath build() {
            return new UsecShotPath(points);
        }
    }
}
