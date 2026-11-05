package dev.caecorthus.sparkwitch.roles.civilian.bear.smoke.sim;

/**
 * Frozen contract: deterministic smoke flow for one cloud, stepped once per game tick (owner 2026-11-05: real smoke,
 * not a static volume). The canister emits at {@link SmokeSimGrid#sourceIndex()}; hot smoke rises, spreads under
 * ceilings and banks down, leaks out of vents, is dragged by the wind outside, and thins out after the emission ends.
 * Pure Java with no Minecraft types: the same grid, seed and step count give bit-identical densities on every client
 * (fixed iteration order, float math, {@code StrictMath} only). Not thread-safe; the client owns one per cloud.
 * SPIKE STUB: the simulation package replaces the body.
 * 冻结契约：一团烟的确定性流动模拟，每个游戏 tick 推进一步（所有者 2026-11-05：真实的烟，不是静止体积）。弹体在
 * {@link SmokeSimGrid#sourceIndex()} 喷烟；热烟上升、沿天花板铺开并向下压，从漏口漏出，在车外被风拖走，停喷后变稀散去。
 * 纯 Java、不含 Minecraft 类型：相同的网格、种子与步数在所有客户端得到逐位相同的浓度（固定遍历顺序、float 运算、
 * 只用 {@code StrictMath}）。非线程安全；客户端每团烟持有一个。原型占位：由模拟工作包替换实现。
 */
public final class SmokeSim {
    private final SmokeSimGrid grid;
    private final float[] density;
    private int simulatedTicks;

    public SmokeSim(SmokeSimGrid grid) {
        this.grid = grid;
        this.density = new float[grid.sizeX() * grid.sizeY() * grid.sizeZ()];
    }

    public SmokeSimGrid grid() {
        return grid;
    }

    /**
     * Steps until {@code ageTicks} ticks after the burst have been simulated, doing at most {@code maxSteps} steps in
     * this call (late joiners catch up over a few frames). Returns true when any density changed.
     * 推进到爆开后 {@code ageTicks} tick，本次最多 {@code maxSteps} 步（中途加入的玩家分几帧追上）。浓度有变化时返回 true。
     */
    public boolean advanceTo(int ageTicks, int maxSteps) {
        boolean changed = false;
        int steps = 0;
        while (simulatedTicks < ageTicks && steps < maxSteps) {
            simulatedTicks++;
            steps++;
            changed = true;
        }
        return changed;
    }

    public int simulatedTicks() {
        return simulatedTicks;
    }

    /** Visible density 0..1 of a cell. / 某格的可见浓度 0..1。 */
    public float density(int index) {
        return density[index];
    }

    /**
     * Explosion impulse: removes the smoke inside a sphere given in cell coordinates (cell centres at i + 0.5); the
     * flow then refills the hole by itself. / 爆炸冲击：清除以格坐标给出的球内的烟（格中心为 i + 0.5），之后由流动自行填回。
     */
    public void clearSphere(double centerX, double centerY, double centerZ, double radiusCells) {
    }

    /** True once the emission has ended and no visible smoke is left. / 已停喷且不再有可见的烟时为 true。 */
    public boolean finished() {
        return simulatedTicks >= grid.emitTicks() + 1;
    }
}
