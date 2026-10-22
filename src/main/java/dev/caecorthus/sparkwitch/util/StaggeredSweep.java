package dev.caecorthus.sparkwitch.util;

/**
 * Spreads a per-player sweep over its interval: each entity is due once per {@code intervalTicks}, offset by its id, so
 * a full server never runs every player's sweep on the same tick. Owners keep their own interval constants.
 * 把逐玩家清扫分摊到整个间隔：每个实体按其 id 错开，每 {@code intervalTicks} 刻到期一次，满员服务器不会在同一刻清扫所有
 * 玩家。各调用方保留自己的间隔常量。
 */
public final class StaggeredSweep {
    private StaggeredSweep() {
    }

    public static boolean isDue(long worldTime, int entityId, int intervalTicks) {
        return Math.floorMod(worldTime + entityId, intervalTicks) == 0;
    }
}
