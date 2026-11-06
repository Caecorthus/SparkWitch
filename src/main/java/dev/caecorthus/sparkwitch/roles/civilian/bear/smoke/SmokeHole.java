package dev.caecorthus.sparkwitch.roles.civilian.bear.smoke;

/**
 * Frozen contract: a temporary hole in every smoke cloud it overlaps, a capsule from {@code (x, y, z)} to
 * {@code (endX, endY, endZ)} of {@code radius} blocks. A blast hole is the sphere case (end equals centre, the
 * six-argument constructor): it stays fully open, then closes from the edge inward during its last
 * {@link SmokeTimeline#HOLE_CLOSE_TICKS} ticks (owner 2026-11-05: closes after about 2.5 s). A bullet tunnel (BEAR WP5,
 * {@link SmokeCloudService#carveTunnel}) is the capsule case with its own shorter, softer timeline
 * ({@link SmokeTimeline#TUNNEL_DURATION_TICKS}, {@link SmokeTimeline#TUNNEL_CLOSE_TICKS},
 * {@link SmokeTimeline#TUNNEL_EDGE}). {@code startTime} is {@code World#getTime()} at the explosion or shot.
 * 冻结契约：所有相交烟团中的临时空洞，是从 {@code (x, y, z)} 到 {@code (endX, endY, endZ)}、半径 {@code radius} 格的胶囊。
 * 爆炸空洞是球形特例（终点等于中心，即六参数构造）：先完全敞开，最后 {@link SmokeTimeline#HOLE_CLOSE_TICKS} tick 从边缘
 * 向内合上（所有者 2026-11-05：约 2.5 秒后合上）。子弹通道（BEAR WP5，{@link SmokeCloudService#carveTunnel}）是胶囊情形，
 * 有自己更短、更柔和的时间线。{@code startTime} 为爆炸或开枪时的 {@code World#getTime()}。
 */
public record SmokeHole(double x, double y, double z, float radius, long startTime, int durationTicks,
                        double endX, double endY, double endZ) {
    public SmokeHole {
        if (!(radius > 0.0F) || durationTicks <= 0) {
            throw new IllegalArgumentException("bad smoke hole");
        }
    }

    /** A spherical (blast) hole: the end point is the centre. / 球形（爆炸）空洞：终点即中心。 */
    public SmokeHole(double x, double y, double z, float radius, long startTime, int durationTicks) {
        this(x, y, z, radius, startTime, durationTicks, x, y, z);
    }

    /** True for a capsule whose end differs from its start. / 终点与起点不同的胶囊为 true。 */
    public boolean isTunnel() {
        return endX != x || endY != y || endZ != z;
    }

    public boolean expired(long worldTime) {
        return worldTime - startTime >= durationTicks;
    }

    /**
     * Distance from a world point to the hole's axis segment (to the centre for a sphere).
     * 世界坐标点到空洞轴线段的距离（球形时为到中心的距离）。
     */
    public double distanceTo(double px, double py, double pz) {
        return SmokeTunnels.distanceToSegment(px, py, pz, x, y, z, endX, endY, endZ);
    }
}
