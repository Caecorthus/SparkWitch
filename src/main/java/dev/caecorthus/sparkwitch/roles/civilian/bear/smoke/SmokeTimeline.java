package dev.caecorthus.sparkwitch.roles.civilian.bear.smoke;

import java.util.List;

/**
 * Frozen contract: the pure time rules of a BEAR smoke cloud, shared by server and client (owner 2026-11-05: grows
 * for 2 s, stays full for 18 s, fades over 3 s; open-sky smoke lives half as long because the train is moving).
 * Every cell of a cloud starts fading at the same moment, so the cloud thins as a whole instead of shrinking.
 * 冻结契约：BEAR 烟团的纯时间规则，服务端与客户端共用（所有者 2026-11-05：扩散 2 秒、满烟 18 秒、3 秒散去；
 * 列车在跑，露天的烟寿命减半）。同一团烟的所有格同时开始消散，烟是整体变淡而不是缩小。
 */
public final class SmokeTimeline {
    public static final int GROW_TICKS = 40;
    public static final int FULL_TICKS = 360;
    public static final int FADE_TICKS = 60;
    /** A newly reached cell ramps from 0 to full over this many ticks, softening the growing front. / 新到达的格在这段时间内从 0 升到满，使扩散前沿柔和。 */
    public static final int RAMP_IN_TICKS = 6;
    public static final int HOLE_DURATION_TICKS = 50;
    public static final int HOLE_CLOSE_TICKS = 20;
    /** Soft edge width of a hole, in blocks. / 空洞软边宽度（格）。 */
    public static final float HOLE_EDGE = 0.5F;

    private SmokeTimeline() {
    }

    /** Ticks after spawn when the whole cloud is gone. / 整团烟消失的时刻（相对爆开的 tick）。 */
    public static int lifetimeTicks() {
        return GROW_TICKS + FULL_TICKS + FADE_TICKS;
    }

    /**
     * Density 0..1 of a present cell.
     * 有烟格的浓度 0..1。
     *
     * @param ageTicks    ticks since {@link SmokeCloudSnapshot#spawnTime()}, fractional (partial tick) allowed
     * @param arrivalTick the cell's arrival tick
     * @param openSky     whether the cell sees the sky
     */
    public static float cellDensity(float ageTicks, int arrivalTick, boolean openSky) {
        float sinceArrival = ageTicks - arrivalTick;
        if (sinceArrival <= 0.0F) {
            return 0.0F;
        }
        float rampIn = Math.min(1.0F, sinceArrival / RAMP_IN_TICKS);
        float full = openSky ? FULL_TICKS * 0.5F : FULL_TICKS;
        float fade = openSky ? FADE_TICKS * 0.5F : FADE_TICKS;
        float fadeStart = GROW_TICKS + full;
        float fadeOut = ageTicks <= fadeStart ? 1.0F : Math.max(0.0F, 1.0F - (ageTicks - fadeStart) / fade);
        return rampIn * fadeOut;
    }

    /** True once no cell of any cloud can still hold smoke. / 任何格都不可能再有烟时为 true。 */
    public static boolean expired(float ageTicks) {
        return ageTicks >= lifetimeTicks();
    }

    /**
     * Density multiplier 0..1 that the given holes leave at a world point (1 = untouched).
     * 给定空洞在某世界坐标处留下的浓度系数 0..1（1 = 不受影响）。
     */
    public static float holeFactor(double x, double y, double z, List<SmokeHole> holes, float worldTime) {
        float factor = 1.0F;
        for (int i = 0, n = holes.size(); i < n; i++) {
            SmokeHole hole = holes.get(i);
            float age = worldTime - hole.startTime();
            if (age < 0.0F || age >= hole.durationTicks()) {
                continue;
            }
            float closeStart = hole.durationTicks() - HOLE_CLOSE_TICKS;
            float radius = age <= closeStart
                    ? hole.radius()
                    : hole.radius() * Math.max(0.0F, 1.0F - (age - closeStart) / HOLE_CLOSE_TICKS);
            if (radius <= 0.0F) {
                continue;
            }
            double dx = x - hole.x();
            double dy = y - hole.y();
            double dz = z - hole.z();
            float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            factor *= smoothstep(radius - HOLE_EDGE, radius, distance);
            if (factor <= 0.0F) {
                return 0.0F;
            }
        }
        return factor;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0.0F, Math.min(1.0F, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0F - 2.0F * t);
    }
}
