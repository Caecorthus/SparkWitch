package dev.caecorthus.sparkwitch.roles.civilian.bear.smoke;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.List;

/**
 * Pure half-block cell geometry over a {@link SmokeBlockView}, for one flood fill (block reads are cached). Cell
 * {@code c} on an axis spans {@code [c * 0.5, (c + 1) * 0.5)}, so its centre sits at block-local 0.25 or 0.75.
 * A cell is open when its centre lies strictly inside none of its block's collision boxes. A step to a face neighbour
 * is free when the segment between the two centres crosses no collision box: glass panes, closed doors and thin walls
 * sit between cell centres, so they stop smoke although both neighbouring cells are open. Boxes that only touch the
 * segment or the centre (shared faces) do not block.
 * 基于 {@link SmokeBlockView} 的纯半格几何，供一次泛洪使用（方块读取会缓存）。某轴上的格 {@code c} 覆盖
 * {@code [c * 0.5, (c + 1) * 0.5)}，其中心位于方块局部 0.25 或 0.75。格中心不严格位于其方块任何碰撞箱内时，该格可容烟。
 * 走向面邻格时，两格中心之间的线段不穿过任何碰撞箱才可通行：玻璃板、关着的门与薄墙夹在格中心之间，所以即使两侧的格都
 * 可容烟，烟也会被挡住。只与线段或中心相切（共用面）的碰撞箱不阻挡。
 */
final class SmokeCells {
    static final int X = 0;
    static final int Y = 1;
    static final int Z = 2;
    /** Cached marker for a block smoke can never enter. / 烟永远进不去的方块的缓存标记。 */
    private static final List<Box> CLOSED = List.of(new Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));

    private final SmokeBlockView view;
    private final Long2ObjectOpenHashMap<List<Box>> blocks = new Long2ObjectOpenHashMap<>();
    private final Long2BooleanOpenHashMap sky = new Long2BooleanOpenHashMap();

    SmokeCells(SmokeBlockView view) {
        this.view = view;
    }

    /** Cell index on one axis of a world coordinate. / 世界坐标在某轴上的格索引。 */
    static int cellOf(double coordinate) {
        return (int) Math.floor(coordinate / SmokeCloudSnapshot.CELL_SIZE);
    }

    /** World coordinate of a cell centre. / 格中心的世界坐标。 */
    static double centerOf(int cell) {
        return (cell + 0.5) * SmokeCloudSnapshot.CELL_SIZE;
    }

    boolean isOpen(int cx, int cy, int cz) {
        List<Box> boxes = boxes(cx >> 1, cy >> 1, cz >> 1);
        if (boxes == CLOSED) {
            return false;
        }
        double lx = local(cx);
        double ly = local(cy);
        double lz = local(cz);
        for (int i = 0, n = boxes.size(); i < n; i++) {
            Box box = boxes.get(i);
            if (box.minX < lx && lx < box.maxX && box.minY < ly && ly < box.maxY && box.minZ < lz && lz < box.maxZ) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether smoke may step from an open cell to its open face neighbour {@code sign} (+1 or -1) along {@code axis}.
     * 烟能否从一个可容烟格沿 {@code axis} 方向 {@code sign}（+1 或 -1）走到相邻的可容烟格。
     */
    boolean canStep(int cx, int cy, int cz, int axis, int sign) {
        int from = axis == X ? cx : axis == Y ? cy : cz;
        int to = from + sign;
        int fromBlock = from >> 1;
        int toBlock = to >> 1;
        int bx = cx >> 1;
        int by = cy >> 1;
        int bz = cz >> 1;
        double lx = local(cx);
        double ly = local(cy);
        double lz = local(cz);
        if (fromBlock == toBlock) {
            return !segmentHits(boxes(bx, by, bz), axis, 0.25, 0.75, lx, ly, lz);
        }
        // The segment crosses a block face: each block is tested on its own half of it.
        // 线段跨过方块面：两个方块各自检查属于自己的那一半。
        boolean forward = sign > 0;
        if (segmentHits(boxes(bx, by, bz), axis, forward ? 0.75 : 0.0, forward ? 1.0 : 0.25, lx, ly, lz)) {
            return false;
        }
        int nx = axis == X ? toBlock : bx;
        int ny = axis == Y ? toBlock : by;
        int nz = axis == Z ? toBlock : bz;
        return !segmentHits(boxes(nx, ny, nz), axis, forward ? 0.0 : 0.75, forward ? 0.25 : 1.0, lx, ly, lz);
    }

    boolean seesSky(int cx, int cy, int cz) {
        int bx = cx >> 1;
        int by = cy >> 1;
        int bz = cz >> 1;
        long key = BlockPos.asLong(bx, by, bz);
        if (sky.containsKey(key)) {
            return sky.get(key);
        }
        boolean visible = view.seesSky(bx, by, bz);
        sky.put(key, visible);
        return visible;
    }

    private List<Box> boxes(int bx, int by, int bz) {
        long key = BlockPos.asLong(bx, by, bz);
        List<Box> boxes = blocks.get(key);
        if (boxes == null) {
            List<Box> read = view.collisionBoxes(bx, by, bz);
            boxes = read == null ? CLOSED : read;
            blocks.put(key, boxes);
        }
        return boxes;
    }

    /**
     * Whether a box crosses the open axis-aligned segment {@code (a0, a1)} on {@code axis} through the local point's
     * other two coordinates. / 碰撞箱是否穿过沿 {@code axis} 的开区间线段 {@code (a0, a1)}（另两轴取局部点的坐标）。
     */
    private static boolean segmentHits(List<Box> boxes, int axis, double a0, double a1,
                                       double lx, double ly, double lz) {
        if (boxes == CLOSED) {
            return true;
        }
        for (int i = 0, n = boxes.size(); i < n; i++) {
            Box box = boxes.get(i);
            boolean hits = switch (axis) {
                case X -> box.minX < a1 && box.maxX > a0
                        && box.minY < ly && ly < box.maxY && box.minZ < lz && lz < box.maxZ;
                case Y -> box.minY < a1 && box.maxY > a0
                        && box.minX < lx && lx < box.maxX && box.minZ < lz && lz < box.maxZ;
                default -> box.minZ < a1 && box.maxZ > a0
                        && box.minX < lx && lx < box.maxX && box.minY < ly && ly < box.maxY;
            };
            if (hits) {
                return true;
            }
        }
        return false;
    }

    /** Block-local centre of a cell on one axis: 0.25 or 0.75. / 格中心在某轴上的方块局部坐标：0.25 或 0.75。 */
    private static double local(int cell) {
        return (cell & 1) == 0 ? 0.25 : 0.75;
    }
}
