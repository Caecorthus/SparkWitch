package dev.caecorthus.sparkwitch.roles.civilian.bear.smoke;

import java.util.Objects;

/**
 * Frozen contract: one BEAR smoke cloud's shape and timeline, computed once by the server and sent to clients once.
 * The shape is a box of half-block cells ({@link #CELL_SIZE}); cell {@code (i, j, k)} covers world
 * {@code [(minCellX + i) * 0.5, (minCellX + i + 1) * 0.5)} on X (likewise Y and Z) and is stored at
 * {@link #index(int, int, int)}. A cell byte is {@link #EMPTY} (no smoke) or carries the arrival tick plus one in its
 * low seven bits (arrival 0..126 ticks after {@link #spawnTime}) and {@link #OPEN_SKY_BIT} when the cell sees the sky
 * (it then lives half as long, see {@link SmokeTimeline}). {@code spawnTime} is {@code World#getTime()} at the burst;
 * clients compare it with their synced world time.
 * 冻结契约：BEAR 一团烟的形状与时间线，由服务端一次算好、一次发给客户端。形状是半格小格组成的盒子
 * （{@link #CELL_SIZE}）；格 {@code (i, j, k)} 覆盖世界 X 区间 {@code [(minCellX + i) * 0.5, (minCellX + i + 1) * 0.5)}
 * （Y、Z 同理），存于 {@link #index(int, int, int)}。格字节为 {@link #EMPTY}（无烟），否则低 7 位为到达 tick + 1
 * （相对 {@link #spawnTime} 的 0..126 tick），见天的格另带 {@link #OPEN_SKY_BIT}（寿命减半，见 {@link SmokeTimeline}）。
 * {@code spawnTime} 为爆开时的 {@code World#getTime()}，客户端用同步后的世界时间比较。
 */
public record SmokeCloudSnapshot(
        int cloudId,
        int minCellX,
        int minCellY,
        int minCellZ,
        int sizeX,
        int sizeY,
        int sizeZ,
        byte[] cells,
        long spawnTime
) {
    /** Cell edge in blocks. / 小格边长（格）。 */
    public static final float CELL_SIZE = 0.5F;
    public static final int EMPTY = 0;
    public static final int OPEN_SKY_BIT = 0x80;
    public static final int ARRIVAL_MASK = 0x7F;
    /** Largest arrival tick a cell byte can carry. / 格字节可表达的最大到达 tick。 */
    public static final int MAX_ARRIVAL_TICK = ARRIVAL_MASK - 1;
    /** Hard cap on the box volume in cells, so a bad packet cannot allocate a huge array. / 盒子体积上限（格），防止异常数据包分配超大数组。 */
    public static final int MAX_BOX_CELLS = 64 * 64 * 64;

    public SmokeCloudSnapshot {
        Objects.requireNonNull(cells, "cells");
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("non-positive smoke box size");
        }
        long volume = (long) sizeX * sizeY * sizeZ;
        if (volume > MAX_BOX_CELLS || cells.length != volume) {
            throw new IllegalArgumentException("smoke box size " + volume + " does not match cells " + cells.length);
        }
    }

    /** Encodes one present cell. / 编码一个有烟的格。 */
    public static byte encodeCell(int arrivalTick, boolean openSky) {
        int arrival = Math.max(0, Math.min(MAX_ARRIVAL_TICK, arrivalTick));
        return (byte) ((arrival + 1) | (openSky ? OPEN_SKY_BIT : 0));
    }

    public int index(int i, int j, int k) {
        return i + sizeX * (j + sizeY * k);
    }

    public boolean present(int index) {
        return (cells[index] & ARRIVAL_MASK) != EMPTY;
    }

    /** Arrival tick of a present cell (undefined for empty cells). / 有烟格的到达 tick（空格无意义）。 */
    public int arrivalTick(int index) {
        return (cells[index] & ARRIVAL_MASK) - 1;
    }

    public boolean openSky(int index) {
        return (cells[index] & OPEN_SKY_BIT) != 0;
    }

    /** World coordinate of the box's minimum corner. / 盒子最小角的世界坐标。 */
    public double minX() {
        return minCellX * (double) CELL_SIZE;
    }

    public double minY() {
        return minCellY * (double) CELL_SIZE;
    }

    public double minZ() {
        return minCellZ * (double) CELL_SIZE;
    }

    public int presentCount() {
        int count = 0;
        for (int i = 0; i < cells.length; i++) {
            if (present(i)) {
                count++;
            }
        }
        return count;
    }
}
