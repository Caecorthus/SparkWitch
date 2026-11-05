package dev.caecorthus.sparkwitch.roles.civilian.bear.smoke;

import it.unimi.dsi.fastutil.longs.LongHeapPriorityQueue;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Pure BEAR smoke shape (owner 2026-11-05: about 200 block³ per cloud, hot smoke). A weighted Dijkstra over half-block
 * cells spreads from the seed through open space only ({@link SmokeCells}); it stops at {@link #CELL_BUDGET} cells or
 * at {@link #MAX_PATH_COST}, and never leaves a box of {@code 2 * REACH + 1} cells per axis. Steps go to all 26
 * neighbours with anisotropic Euclidean costs, so an open-field cloud is a round dome rather than the diamond a
 * 6-neighbour fill gives; a diagonal step is taken only when some axis-by-axis path to it is free, so it never cuts
 * through a corner a face step could not pass. Cells pop in cost order, and a cell's arrival tick is its pop rank
 * spread over {@link SmokeTimeline#GROW_TICKS}: the smoke comes out at a constant volume rate, so the front is fast at
 * first and slows as it spreads.
 * 纯 BEAR 烟雾形状（所有者 2026-11-05：每团约 200 立方格，热烟）。在半格上做加权 Dijkstra，只经可容烟空间
 * （{@link SmokeCells}）从起点蔓延；达到 {@link #CELL_BUDGET} 格或 {@link #MAX_PATH_COST} 即停，且不会超出每轴
 * {@code 2 * REACH + 1} 格的盒子。可走向全部 26 个邻格，代价为各向异性的欧氏长度，因此空旷处的烟团是圆顶而不是 6 邻接
 * 泛洪得到的菱形；只有存在逐轴可通的路径时才允许斜走，所以斜步永远不会穿过面步过不去的拐角。格按代价顺序出队，
 * 到达 tick 为出队名次在 {@link SmokeTimeline#GROW_TICKS} 内的均匀分布：烟按恒定体积速率涌出，前沿先快后慢。
 */
public final class SmokeCloudShape {
    /** Cells per cloud: 1600 half-block cells = 200 block³ (owner 2026-11-05). / 每团格数：1600 个半格 = 200 立方格。 */
    public static final int CELL_BUDGET = 1600;
    /**
     * Path cost cap in sideways half-block steps: 32 = 16 blocks, so a 2-wide, 3-tall corridor (48 cells per block of
     * length) fills about 31 blocks when the burst is in the middle; the box limit (15.5 blocks each way) binds first.
     * 路径代价上限，以水平半格步计：32 = 16 格，因此 2 宽 3 高的走廊（每格长度 48 个小格）在中间爆开时约填满 31 格；
     * 盒子上限（每侧 15.5 格）先生效。
     */
    public static final float MAX_PATH_COST = 32.0F;
    /**
     * Hot smoke rises and hugs ceilings: a step up costs 0.85, so smoke reaches 1/0.85 = 1.18x as high as it spreads
     * sideways and fills a room's upper part first; a step down costs 1.25, so it sinks only 0.8x as far and barely
     * pours down stairs or open hatches. Gentle on purpose: a 3-tall train room still fills floor to ceiling.
     * 热烟上升并贴着天花板：向上一步代价 0.85，烟向上可达水平距离的 1/0.85 = 1.18 倍，先填满房间上部；向下一步代价 1.25，
     * 只下沉 0.8 倍，几乎不会顺楼梯或敞开的舱口流下。刻意保持温和：3 格高的车厢仍会从地板填到天花板。
     */
    public static final float UP_COST = 0.85F;
    public static final float SIDE_COST = 1.0F;
    public static final float DOWN_COST = 1.25F;
    /**
     * Largest cell offset from the seed on any axis: the box is at most 63 cells (31.5 blocks) per axis, under the
     * 64-cell wire limit. / 任意轴上距起点的最大格偏移：盒子每轴最多 63 格（31.5 方块），低于线格式的 64 格上限。
     */
    static final int REACH = 31;
    /** How far the seed may step back out of a solid impact, in blocks. / 起点从实心命中处退回的最大距离（格）。 */
    static final double SEED_BACKOFF = 1.0;

    private static final int WINDOW = 2 * REACH + 1;
    private static final byte KNOWN = 1;
    private static final byte OPEN = 2;
    private static final byte SETTLED = 4;
    private static final int[] MOVE_DX;
    private static final int[] MOVE_DY;
    private static final int[] MOVE_DZ;
    private static final float[] MOVE_COST;

    static {
        MOVE_DX = new int[26];
        MOVE_DY = new int[26];
        MOVE_DZ = new int[26];
        MOVE_COST = new float[26];
        int n = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    double vertical = dy > 0 ? UP_COST : dy < 0 ? DOWN_COST : 0.0;
                    MOVE_DX[n] = dx;
                    MOVE_DY[n] = dy;
                    MOVE_DZ[n] = dz;
                    MOVE_COST[n] = (float) Math.sqrt(SIDE_COST * SIDE_COST * (dx * dx + dz * dz)
                            + vertical * vertical * dy * dy);
                    n++;
                }
            }
        }
    }

    private SmokeCloudShape() {
    }

    /**
     * One flood fill: the seed cell and every reached cell's offset from it, in pop (arrival) order.
     * 一次泛洪的结果：起点格，以及每个到达格相对起点的偏移，按出队（到达）顺序。
     */
    record Fill(int seedX, int seedY, int seedZ, int[] dx, int[] dy, int[] dz, int count) {
    }

    /**
     * Builds the cloud for a burst at {@code impact}, or null when no open cell is near it.
     * 为在 {@code impact} 处的爆开生成烟团；附近没有可容烟格时返回 null。
     */
    @Nullable
    public static SmokeCloudSnapshot build(int cloudId, long spawnTime, Vec3d impact, @Nullable Vec3d incomingDirection,
                                           SmokeBlockView view) {
        SmokeCells cells = new SmokeCells(view);
        int[] seed = seed(cells, impact, incomingDirection);
        if (seed == null) {
            return null;
        }
        Fill fill = fill(cells, seed[0], seed[1], seed[2], CELL_BUDGET);
        return toSnapshot(cloudId, spawnTime, fill, cells);
    }

    /**
     * Seed cell: the impact cell when open; else the first open cell stepping back along {@code -incomingDirection}
     * (quarter-block steps, at most {@link #SEED_BACKOFF}); else the open cell nearest to the impact within
     * {@link #SEED_BACKOFF}. Null when none.
     * 起点格：命中格可容烟时取命中格；否则沿 {@code -incomingDirection} 以四分之一格步长后退（至多 {@link #SEED_BACKOFF}）
     * 取第一个可容烟格；再否则取距命中点 {@link #SEED_BACKOFF} 内最近的可容烟格。都没有时返回 null。
     */
    @Nullable
    static int[] seed(SmokeCells cells, Vec3d impact, @Nullable Vec3d incomingDirection) {
        int ix = SmokeCells.cellOf(impact.x);
        int iy = SmokeCells.cellOf(impact.y);
        int iz = SmokeCells.cellOf(impact.z);
        if (cells.isOpen(ix, iy, iz)) {
            return new int[]{ix, iy, iz};
        }
        if (incomingDirection != null && incomingDirection.lengthSquared() > 1.0E-8) {
            Vec3d back = incomingDirection.normalize().multiply(-1.0);
            for (double t = 0.25; t <= SEED_BACKOFF + 1.0E-9; t += 0.25) {
                int cx = SmokeCells.cellOf(impact.x + back.x * t);
                int cy = SmokeCells.cellOf(impact.y + back.y * t);
                int cz = SmokeCells.cellOf(impact.z + back.z * t);
                if (cells.isOpen(cx, cy, cz)) {
                    return new int[]{cx, cy, cz};
                }
            }
        }
        int[] best = null;
        double bestDistance = SEED_BACKOFF * SEED_BACKOFF + 1.0E-9;
        int range = (int) Math.ceil(SEED_BACKOFF / SmokeCloudSnapshot.CELL_SIZE);
        for (int oz = -range; oz <= range; oz++) {
            for (int oy = range; oy >= -range; oy--) {
                for (int ox = -range; ox <= range; ox++) {
                    int cx = ix + ox;
                    int cy = iy + oy;
                    int cz = iz + oz;
                    double ddx = SmokeCells.centerOf(cx) - impact.x;
                    double ddy = SmokeCells.centerOf(cy) - impact.y;
                    double ddz = SmokeCells.centerOf(cz) - impact.z;
                    double distance = ddx * ddx + ddy * ddy + ddz * ddz;
                    // Strictly nearer wins, so ties keep the first (upper) candidate. / 严格更近才替换，并列时保留先遇到的（更高的）候选。
                    if (distance < bestDistance && cells.isOpen(cx, cy, cz)) {
                        bestDistance = distance;
                        best = new int[]{cx, cy, cz};
                    }
                }
            }
        }
        return best;
    }

    /** The weighted flood fill from an open seed cell. / 从可容烟起点格开始的加权泛洪。 */
    static Fill fill(SmokeCells cells, int seedX, int seedY, int seedZ, int budget) {
        int volume = WINDOW * WINDOW * WINDOW;
        float[] distance = new float[volume];
        Arrays.fill(distance, Float.POSITIVE_INFINITY);
        byte[] state = new byte[volume];
        int[] outX = new int[budget];
        int[] outY = new int[budget];
        int[] outZ = new int[budget];
        int count = 0;
        LongHeapPriorityQueue heap = new LongHeapPriorityQueue();
        int seedIndex = index(0, 0, 0);
        distance[seedIndex] = 0.0F;
        state[seedIndex] = KNOWN | OPEN;
        heap.enqueue(entry(0.0F, seedIndex));
        Walker walker = new Walker(cells, state, seedX, seedY, seedZ);
        while (!heap.isEmpty() && count < budget) {
            long top = heap.dequeueLong();
            int at = (int) top;
            if ((state[at] & SETTLED) != 0) {
                continue;
            }
            state[at] |= SETTLED;
            float cost = Float.intBitsToFloat((int) (top >>> 32));
            int ox = at % WINDOW - REACH;
            int oy = at / WINDOW % WINDOW - REACH;
            int oz = at / (WINDOW * WINDOW) - REACH;
            outX[count] = ox;
            outY[count] = oy;
            outZ[count] = oz;
            count++;
            for (int m = 0; m < MOVE_COST.length; m++) {
                int nx = ox + MOVE_DX[m];
                int ny = oy + MOVE_DY[m];
                int nz = oz + MOVE_DZ[m];
                if (Math.abs(nx) > REACH || Math.abs(ny) > REACH || Math.abs(nz) > REACH) {
                    continue;
                }
                int next = index(nx, ny, nz);
                float nextCost = cost + MOVE_COST[m];
                if ((state[next] & SETTLED) != 0 || nextCost > MAX_PATH_COST || nextCost >= distance[next]) {
                    continue;
                }
                if (!walker.reachable(ox, oy, oz, MOVE_DX[m], MOVE_DY[m], MOVE_DZ[m])) {
                    continue;
                }
                distance[next] = nextCost;
                heap.enqueue(entry(nextCost, next));
            }
        }
        return new Fill(seedX, seedY, seedZ, outX, outY, outZ, count);
    }

    /**
     * Snapshot of a fill: the tight box around the reached cells, arrival by rank, sky flag per block.
     * 泛洪结果的快照：紧贴到达格的盒子，按名次给出到达 tick，按方块给出露天标记。
     */
    static SmokeCloudSnapshot toSnapshot(int cloudId, long spawnTime, Fill fill, SmokeCells cells) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int n = 0; n < fill.count(); n++) {
            minX = Math.min(minX, fill.dx()[n]);
            minY = Math.min(minY, fill.dy()[n]);
            minZ = Math.min(minZ, fill.dz()[n]);
            maxX = Math.max(maxX, fill.dx()[n]);
            maxY = Math.max(maxY, fill.dy()[n]);
            maxZ = Math.max(maxZ, fill.dz()[n]);
        }
        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        byte[] bytes = new byte[sizeX * sizeY * sizeZ];
        for (int n = 0; n < fill.count(); n++) {
            int cx = fill.seedX() + fill.dx()[n];
            int cy = fill.seedY() + fill.dy()[n];
            int cz = fill.seedZ() + fill.dz()[n];
            int arrival = (int) ((long) SmokeTimeline.GROW_TICKS * n / fill.count());
            int i = fill.dx()[n] - minX;
            int j = fill.dy()[n] - minY;
            int k = fill.dz()[n] - minZ;
            bytes[i + sizeX * (j + sizeY * k)] = SmokeCloudSnapshot.encodeCell(arrival, cells.seesSky(cx, cy, cz));
        }
        return new SmokeCloudSnapshot(cloudId, fill.seedX() + minX, fill.seedY() + minY, fill.seedZ() + minZ,
                sizeX, sizeY, sizeZ, bytes, spawnTime);
    }

    private static int index(int ox, int oy, int oz) {
        return (ox + REACH) + WINDOW * ((oy + REACH) + WINDOW * (oz + REACH));
    }

    /** Non-negative costs keep their order as raw float bits; the index breaks ties. / 非负代价的浮点位序与数值序一致；索引打破并列。 */
    private static long entry(float cost, int index) {
        return ((long) Float.floatToIntBits(cost) << 32) | (index & 0xFFFFFFFFL);
    }

    /** Axis-by-axis step checks with a per-fill open-cell cache. / 带单次泛洪可容烟缓存的逐轴步进检查。 */
    private static final class Walker {
        private final SmokeCells cells;
        private final byte[] state;
        private final int seedX;
        private final int seedY;
        private final int seedZ;

        Walker(SmokeCells cells, byte[] state, int seedX, int seedY, int seedZ) {
            this.cells = cells;
            this.state = state;
            this.seedX = seedX;
            this.seedY = seedY;
            this.seedZ = seedZ;
        }

        /**
         * True when some monotone axis-by-axis path from the offset cell to {@code (dx, dy, dz)} away is free.
         * 从偏移格到 {@code (dx, dy, dz)} 处存在一条逐轴单调且可通的路径时为 true。
         */
        boolean reachable(int ox, int oy, int oz, int dx, int dy, int dz) {
            if (dx == 0 && dy == 0 && dz == 0) {
                return true;
            }
            return dx != 0 && step(ox, oy, oz, SmokeCells.X, dx) && reachable(ox + dx, oy, oz, 0, dy, dz)
                    || dy != 0 && step(ox, oy, oz, SmokeCells.Y, dy) && reachable(ox, oy + dy, oz, dx, 0, dz)
                    || dz != 0 && step(ox, oy, oz, SmokeCells.Z, dz) && reachable(ox, oy, oz + dz, dx, dy, 0);
        }

        private boolean step(int ox, int oy, int oz, int axis, int sign) {
            int tx = axis == SmokeCells.X ? ox + sign : ox;
            int ty = axis == SmokeCells.Y ? oy + sign : oy;
            int tz = axis == SmokeCells.Z ? oz + sign : oz;
            return open(tx, ty, tz) && cells.canStep(seedX + ox, seedY + oy, seedZ + oz, axis, sign);
        }

        private boolean open(int ox, int oy, int oz) {
            int at = index(ox, oy, oz);
            byte known = state[at];
            if ((known & KNOWN) == 0) {
                known = (byte) (known | KNOWN | (cells.isOpen(seedX + ox, seedY + oy, seedZ + oz) ? OPEN : 0));
                state[at] = known;
            }
            return (known & OPEN) != 0;
        }
    }
}
