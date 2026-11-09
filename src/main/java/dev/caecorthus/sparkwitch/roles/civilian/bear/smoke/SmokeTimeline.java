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
    /**
     * A bullet tunnel lives 1.5 s (BEAR WP5 call, CS2-like): fully open for 0.5 s, then its radius shrinks to 0 over
     * {@link #TUNNEL_CLOSE_TICKS}, so the smoke flows back in from the sides.
     * 子弹通道存在 1.5 秒（BEAR WP5 的决定，类似 CS2）：完全敞开 0.5 秒，随后半径在 {@link #TUNNEL_CLOSE_TICKS} 内缩到 0，
     * 烟从两侧流回。
     */
    public static final int TUNNEL_DURATION_TICKS = 30;
    public static final int TUNNEL_CLOSE_TICKS = 20;
    /**
     * Soft edge of a tunnel, narrower than a blast hole's so a 0.35-block bullet tunnel still has a clear core.
     * 通道软边，比爆炸空洞窄，使 0.35 格的子弹通道仍有完全通透的核心。
     */
    public static final float TUNNEL_EDGE = 0.2F;
    /**
     * A tunnel at least this wide is a blast lane (WP3 carves the potion backblast with 0.5): it lives as long as a
     * blast hole instead of a bullet tunnel. / 至少这么宽的通道是爆炸通道（WP3 以 0.5 开出药炮后喷）：寿命与爆炸空洞相同，
     * 而不是子弹通道。
     */
    public static final float BLAST_LANE_RADIUS = 0.5F;

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

    /** Lifetime of a tunnel of {@code radius} blocks. / 半径 {@code radius} 格的通道寿命。 */
    public static int tunnelDurationTicks(float radius) {
        return radius >= BLAST_LANE_RADIUS ? HOLE_DURATION_TICKS : TUNNEL_DURATION_TICKS;
    }

    /** True once no cell of any cloud can still hold smoke. / 任何格都不可能再有烟时为 true。 */
    public static boolean expired(float ageTicks) {
        return ageTicks >= lifetimeTicks();
    }

    /**
     * Density multiplier 0..1 that the given holes leave at a world point (1 = untouched): per hole a smoothstep from
     * {@link #holeRadius} minus {@link #holeEdge} (0) to the radius (1) over the distance to its axis segment.
     * 给定空洞在某世界坐标处留下的浓度系数 0..1（1 = 不受影响）：每个空洞按到其轴线段的距离，从 {@link #holeRadius} 减
     * {@link #holeEdge}（0）到半径（1）做 smoothstep。
     */
    public static float holeFactor(double x, double y, double z, List<SmokeHole> holes, float worldTime) {
        float factor = 1.0F;
        for (int i = 0, n = holes.size(); i < n; i++) {
            SmokeHole hole = holes.get(i);
            float radius = holeRadiusAtAge(hole, worldTime - hole.startTime());
            if (radius <= 0.0F) {
                continue;
            }
            factor *= edgeFactor(radius, holeEdge(hole), (float) hole.distanceTo(x, y, z));
            if (factor <= 0.0F) {
                return 0.0F;
            }
        }
        return factor;
    }

    /**
     * {@link #holeFactor(double, double, double, List, float)} at a whole world tick: the age is taken in
     * {@code long} first, so it stays exact however large the world time grows (a float world time rounds to whole
     * ticks after about 5.8 days and to 2 ticks after 9.7, which a 30-tick tunnel cannot afford).
     * 整数世界 tick 下的 {@link #holeFactor(double, double, double, List, float)}：先以 {@code long} 求年龄，因此无论世界时间
     * 多大都精确（float 世界时间约 5.8 天后只能精确到整 tick，9.7 天后到 2 tick，30 tick 的通道承受不起）。
     */
    public static float holeFactor(double x, double y, double z, List<SmokeHole> holes, long worldTime) {
        float factor = 1.0F;
        for (int i = 0, n = holes.size(); i < n; i++) {
            SmokeHole hole = holes.get(i);
            float radius = holeRadius(hole, worldTime);
            if (radius <= 0.0F) {
                continue;
            }
            factor *= edgeFactor(radius, holeEdge(hole), (float) hole.distanceTo(x, y, z));
            if (factor <= 0.0F) {
                return 0.0F;
            }
        }
        return factor;
    }

    /**
     * Current radius of a hole (0 before it starts and once it ended): full, then shrinking linearly to 0 over the last
     * {@link #HOLE_CLOSE_TICKS} (blast) or {@link #TUNNEL_CLOSE_TICKS} (tunnel) ticks.
     * 空洞当前半径（开始前与结束后为 0）：先保持完整，最后 {@link #HOLE_CLOSE_TICKS}（爆炸）或 {@link #TUNNEL_CLOSE_TICKS}
     * （通道）tick 内线性缩到 0。
     */
    public static float holeRadius(SmokeHole hole, float worldTime) {
        return holeRadiusAtAge(hole, worldTime - hole.startTime());
    }

    /** {@link #holeRadius(SmokeHole, float)} with the age taken exactly in {@code long}. / 以 long 精确求年龄的版本。 */
    public static float holeRadius(SmokeHole hole, long worldTime) {
        return holeRadiusAtAge(hole, (float) (worldTime - hole.startTime()));
    }

    private static float holeRadiusAtAge(SmokeHole hole, float age) {
        if (age < 0.0F || age >= hole.durationTicks()) {
            return 0.0F;
        }
        int close = hole.isTunnel() ? TUNNEL_CLOSE_TICKS : HOLE_CLOSE_TICKS;
        float closeStart = hole.durationTicks() - close;
        return age <= closeStart
                ? hole.radius()
                : hole.radius() * Math.max(0.0F, 1.0F - (age - closeStart) / close);
    }

    /**
     * Density multiplier of one hole of current {@code radius} at {@code distance} from its axis: 0 inside
     * {@code radius - edge}, 1 from {@code radius} on, smoothstep between (the GPU passes mirror it for tunnels).
     * 当前半径 {@code radius} 的单个空洞在距轴线 {@code distance} 处的浓度系数：{@code radius - edge} 以内为 0，
     * {@code radius} 起为 1，其间 smoothstep（GPU 各步骤对通道镜像此式）。
     */
    public static float edgeFactor(float radius, float edge, float distance) {
        return smoothstep(radius - edge, radius, distance);
    }

    /** Soft edge width of a hole: {@link #HOLE_EDGE} or {@link #TUNNEL_EDGE}. / 空洞软边宽度。 */
    public static float holeEdge(SmokeHole hole) {
        return hole.isTunnel() ? TUNNEL_EDGE : HOLE_EDGE;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0.0F, Math.min(1.0F, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0F - 2.0F * t);
    }
}
