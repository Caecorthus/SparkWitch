package dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.sim;

/**
 * Frozen contract: everything the smoke flow simulation needs to know about one cloud's space, fixed at the burst.
 * The server computes it once (a terrain flood fill that marks where smoke MAY go) and every client simulates the
 * smoke inside it on its own, so the grid plus the seed must fully determine the result. Cells are half-block cubes in
 * a box; {@code index = x + sizeX * (y + sizeY * z)}. Axis ids: 0 = X, 1 = Y (up), 2 = Z.
 * 冻结契约：烟雾流动模拟需要知道的一团烟的空间信息，在爆开时固定。服务端只算一次（按地形泛洪标出烟可能到达的范围），
 * 每个客户端各自在其中模拟烟雾，因此网格加种子必须完全决定结果。格子是盒内的半格小立方体；
 * {@code index = x + sizeX * (y + sizeY * z)}。轴编号：0 = X，1 = Y（向上），2 = Z。
 */
public interface SmokeSimGrid {
    int sizeX();

    int sizeY();

    int sizeZ();

    /** Smoke may exist in this cell. / 该格可以有烟。 */
    boolean inRegion(int index);

    /**
     * The face between this cell and its +axis neighbour is closed (wall, glass pane, closed door), even when both
     * cells are in the region. / 该格与其 +axis 方向邻格之间的面是封闭的（墙、玻璃板、关着的门），即使两格都在范围内。
     */
    boolean faceBlocked(int index, int axis);

    /** The cell sees the sky: smoke there is outside the train and the wind drags it. / 该格见天：烟在车外，会被风拖走。 */
    boolean openSky(int index);

    /**
     * The cell borders passable space outside the region (the fill ran out of budget there), so smoke leaks out of it.
     * 该格与范围外的可通行空间相邻（泛洪在此用尽预算），烟会从这里漏出去。
     */
    boolean vent(int index);

    /** Cell where the canister lies and emits. / 弹体所在并喷烟的格。 */
    int sourceIndex();

    /** Wind on open-sky cells, in blocks per second (horizontal). / 见天格上的风速（格/秒，水平）。 */
    float windX();

    float windZ();

    /** Seed for every pseudo-random choice in the simulation. / 模拟中所有伪随机选择的种子。 */
    long seed();

    /** Ticks the canister emits (normally {@code SmokeEmission.EMIT_TICKS}; shorter after an eviction). / 弹体喷烟的 tick 数（通常为 {@code SmokeEmission.EMIT_TICKS}；被淘汰后更短）。 */
    int emitTicks();
}
