package dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone;

import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssListenerRules;

/**
 * Pure Deep Dark Zone timeline, computed once per block at landing; visuals and effects read the same ticks.
 * Every block converts on the landing tick (owner D14: no spread phase), the zone holds {@code ZONE_HOLD_TICKS},
 * then restores over {@code ZONE_RESTORE_TICKS} outermost first, so the zone recedes to its centre.
 * 纯深暗领域时间轴，落地时为每个方块计算一次；视觉与效果读取同一组时刻。所有方块在落地那一刻转换（D14：没有蔓延阶段），
 * 保持 {@code ZONE_HOLD_TICKS}，再在 {@code ZONE_RESTORE_TICKS} 内由外向内恢复，使领域向中心收缩。
 */
public final class DeepDarkZoneSchedule {
    private DeepDarkZoneSchedule() {
    }

    /** Ticks after landing at which the block restores (outer first). / 落地后多少刻恢复（外圈先恢复）。 */
    public static long restoreOffset(double distance) {
        return restoreStartOffset() + Math.round(AbyssListenerRules.ZONE_RESTORE_TICKS * (1.0 - fraction(distance)));
    }

    /** Ticks after landing at which the restore phase starts. / 落地后恢复阶段开始的刻数。 */
    public static long restoreStartOffset() {
        return AbyssListenerRules.ZONE_HOLD_TICKS;
    }

    /** Whole zone lifetime; the centre restores last, exactly at this offset. / 领域总时长；中心最后恢复。 */
    public static long lifetimeTicks() {
        return restoreStartOffset() + AbyssListenerRules.ZONE_RESTORE_TICKS;
    }

    /**
     * Distance as a fraction of the radius, clamped to [0, 1]: a wall block next to a reached cell at the rim may lie
     * slightly beyond the radius and simply restores first.
     * 距离占半径的比例，限制在 [0, 1]：与边缘已到达格相邻的墙方块可能略超出半径，只会最先恢复。
     */
    static double fraction(double distance) {
        if (!(distance > 0.0)) {
            return 0.0;
        }
        return Math.min(1.0, distance / AbyssListenerRules.ZONE_RADIUS);
    }
}
