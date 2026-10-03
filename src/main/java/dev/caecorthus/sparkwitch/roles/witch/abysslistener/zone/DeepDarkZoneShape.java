package dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Pure Deep Dark Zone shape (owner D4): the zone spreads only through the open space the flask landed in. A
 * breadth-first flood fill walks 6-neighbour steps through cells with no collision (air, open doors, decorations),
 * keeping cells whose Euclidean distance from the landing cell is at most the radius; walls, closed doors and any
 * colliding block stop it. Every solid neighbour of a reached cell that passes the eligibility probe converts.
 * 纯深暗领域形状（D4）：领域只在孢瓶落地的空间里蔓延。广度优先的泛洪沿 6 邻接步穿过无碰撞的格子（空气、打开的门、装饰），
 * 只保留与落点格欧氏距离不超过半径的格子；墙、关着的门与任何有碰撞的方块都会挡住它。被到达格子的每个通过资格检查的
 * 实心邻格都会被转换。
 */
public final class DeepDarkZoneShape {
    /**
     * Safety caps only: a radius-8 ball holds about 2,100 lattice cells and the Harpy Express converts about 200-290
     * blocks, so these bound pathological custom maps without changing normal zones.
     * 仅作安全上限：半径 8 的球约有 2100 个格点，Harpy 列车约转换 200-290 个方块；上限只约束异常的自定义地图。
     */
    public static final int MAX_REACHED_CELLS = 4096;
    public static final int MAX_CONVERTED_CELLS = 2048;
    private static final Direction[] STEPS = Direction.values();

    private DeepDarkZoneShape() {
    }

    /** World reads the flood fill needs; the server adapter reads blocks, tests use grids. / 泛洪所需的世界读取。 */
    public interface CellProbe {
        /** The cell has no collision shape (air, an open door, a decoration). / 该格没有碰撞箱。 */
        boolean passable(BlockPos pos);

        /** The cell passes the full eligibility predicate. / 该格通过完整资格判定。 */
        boolean convertible(BlockPos pos);
    }

    /** A block to convert and its distance from the landing cell. / 待转换方块及其到落点格的距离。 */
    public record Target(BlockPos pos, double distance) {
    }

    /**
     * Landing cell: a block hit lands in the cell in front of the struck face (the open side the flask came from);
     * an entity hit or any other result lands in the cell holding the hit point.
     * 落点格：命中方块时落在被击中面前方的格子（孢瓶飞来的空旷一侧）；命中实体或其他结果时落在命中点所在格子。
     */
    public static BlockPos landingCell(HitResult hit) {
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            return blockHit.getBlockPos().offset(blockHit.getSide());
        }
        return BlockPos.ofFloored(hit.getPos());
    }

    /** Snapshot of the blocks to convert, nearest first. / 待转换方块的快照，由近及远。 */
    public static List<Target> collect(BlockPos landing, int radius, CellProbe probe) {
        return collect(landing, radius, probe, MAX_REACHED_CELLS, MAX_CONVERTED_CELLS);
    }

    static List<Target> collect(BlockPos landing, int radius, CellProbe probe, int maxReached, int maxConverted) {
        BlockPos origin = landing.toImmutable();
        long radiusSquared = (long) radius * radius;
        // The landing cell is always reached, even when an entity hit put it inside a thin colliding block.
        // 落点格总被视为已到达，即使实体命中让它落在有碰撞的薄方块里。
        LongOpenHashSet seen = new LongOpenHashSet();
        seen.add(origin.asLong());
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        frontier.add(origin);
        int reached = 1;
        List<Target> targets = new ArrayList<>();
        while (!frontier.isEmpty()) {
            BlockPos cell = frontier.poll();
            for (Direction step : STEPS) {
                BlockPos next = cell.offset(step);
                if (!seen.add(next.asLong())) {
                    continue;
                }
                long distanceSquared = distanceSquared(origin, next);
                if (probe.passable(next)) {
                    if (distanceSquared <= radiusSquared && reached < maxReached) {
                        reached++;
                        frontier.add(next);
                    }
                    continue;
                }
                if (targets.size() < maxConverted && probe.convertible(next)) {
                    targets.add(new Target(next, Math.sqrt(distanceSquared)));
                }
            }
        }
        targets.sort((left, right) -> Double.compare(left.distance(), right.distance()));
        return targets;
    }

    static long distanceSquared(BlockPos from, BlockPos to) {
        long dx = to.getX() - from.getX();
        long dy = to.getY() - from.getY();
        long dz = to.getZ() - from.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
