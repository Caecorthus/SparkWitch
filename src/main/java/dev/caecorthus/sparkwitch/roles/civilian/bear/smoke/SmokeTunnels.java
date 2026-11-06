package dev.caecorthus.sparkwitch.roles.civilian.bear.smoke;

/**
 * Pure capsule geometry of BEAR smoke tunnels (BEAR WP5), shared by the server (clipping a ray to the live clouds) and
 * the client (the CPU optical depth and the per-pixel cull of the GPU passes, whose GLSL mirrors
 * {@link #segmentSegmentDistanceSquared}). Plain doubles, no allocation on the hot paths.
 * BEAR 烟雾通道的纯胶囊几何（BEAR WP5），服务端（把射线裁剪到存活烟团）与客户端（CPU 光学厚度，以及 GPU 各步骤的逐像素剔除，
 * 其 GLSL 镜像 {@link #segmentSegmentDistanceSquared}）共用。只用 double，热路径不分配内存。
 */
public final class SmokeTunnels {
    /**
     * Blocks a tunnel reaches past a cloud's box (beyond its own radius): the visible smoke's domain warp spreads
     * wisps up to {@code SmokeMarchRules.warpReach()} (1.8) outside the box, and a tunnel must cut those too, or a
     * shooter would see a veil at each end. / 通道越过烟团盒子的距离（在自身半径之外）：可见烟雾的域扭曲把烟丝扩散到盒子
     * 外至多 {@code SmokeMarchRules.warpReach()}（1.8）格，通道也必须切开它们，否则射手会在两端看到一层薄幕。
     */
    public static final double CLIP_MARGIN = 2.0;

    private SmokeTunnels() {
    }

    /**
     * Distance from {@code p} to the segment {@code a} → {@code b}; a zero-length segment is the point {@code a}
     * (exactly the old sphere distance). / 点 {@code p} 到线段 {@code a} → {@code b} 的距离；零长度线段即点 {@code a}
     * （与旧的球形距离完全一致）。
     */
    public static double distanceToSegment(double px, double py, double pz,
                                           double ax, double ay, double az, double bx, double by, double bz) {
        double ux = bx - ax;
        double uy = by - ay;
        double uz = bz - az;
        double lengthSquared = ux * ux + uy * uy + uz * uz;
        double t = 0.0;
        if (lengthSquared > 0.0) {
            t = ((px - ax) * ux + (py - ay) * uy + (pz - az) * uz) / lengthSquared;
            t = Math.max(0.0, Math.min(1.0, t));
        }
        double dx = px - (ax + ux * t);
        double dy = py - (ay + uy * t);
        double dz = pz - (az + uz * t);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Parameter span {@code [t0, t1]} of the segment {@code from + (to - from) * t}, {@code t} in [0, 1], inside the
     * box {@code min..max} (slab test), written to {@code out}; false when the segment misses the box.
     * 线段 {@code from + (to - from) * t}（{@code t} 属于 [0, 1]）位于盒子 {@code min..max} 内的参数区间
     * {@code [t0, t1]}（板块法），写入 {@code out}；线段不与盒子相交时返回 false。
     */
    public static boolean clipToBox(double x0, double y0, double z0, double x1, double y1, double z1,
                                    double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                                    double[] out) {
        out[0] = 0.0;
        out[1] = 1.0;
        return slab(x0, x1 - x0, minX, maxX, out) && slab(y0, y1 - y0, minY, maxY, out)
                && slab(z0, z1 - z0, minZ, maxZ, out);
    }

    /** Narrows {@code span} to one axis slab; false when it becomes empty. / 按一个轴的板块收窄区间；变空时返回 false。 */
    private static boolean slab(double origin, double delta, double min, double max, double[] span) {
        if (Math.abs(delta) < 1.0E-12) {
            return origin >= min && origin <= max;
        }
        double a = (min - origin) / delta;
        double b = (max - origin) / delta;
        span[0] = Math.max(span[0], Math.min(a, b));
        span[1] = Math.min(span[1], Math.max(a, b));
        return span[0] <= span[1];
    }

    /**
     * Squared closest distance between the segments {@code p0 → p1} and {@code q0 → q1} (Ericson, Real-Time Collision
     * Detection 5.1.9; degenerate segments are points). The GPU passes mirror it to cull tunnels per pixel.
     * 线段 {@code p0 → p1} 与 {@code q0 → q1} 之间最近距离的平方（Ericson《实时碰撞检测》5.1.9；退化线段视为点）。
     * GPU 各步骤镜像此算法做逐像素的通道剔除。
     */
    public static double segmentSegmentDistanceSquared(double p0x, double p0y, double p0z,
                                                       double p1x, double p1y, double p1z,
                                                       double q0x, double q0y, double q0z,
                                                       double q1x, double q1y, double q1z) {
        double d1x = p1x - p0x;
        double d1y = p1y - p0y;
        double d1z = p1z - p0z;
        double d2x = q1x - q0x;
        double d2y = q1y - q0y;
        double d2z = q1z - q0z;
        double rx = p0x - q0x;
        double ry = p0y - q0y;
        double rz = p0z - q0z;
        double a = d1x * d1x + d1y * d1y + d1z * d1z;
        double e = d2x * d2x + d2y * d2y + d2z * d2z;
        double f = d2x * rx + d2y * ry + d2z * rz;
        double s;
        double t;
        double epsilon = 1.0E-12;
        if (a <= epsilon && e <= epsilon) {
            s = 0.0;
            t = 0.0;
        } else if (a <= epsilon) {
            s = 0.0;
            t = clamp01(f / e);
        } else {
            double c = d1x * rx + d1y * ry + d1z * rz;
            if (e <= epsilon) {
                t = 0.0;
                s = clamp01(-c / a);
            } else {
                double b = d1x * d2x + d1y * d2y + d1z * d2z;
                double denominator = a * e - b * b;
                s = denominator > epsilon ? clamp01((b * f - c * e) / denominator) : 0.0;
                t = (b * s + f) / e;
                if (t < 0.0) {
                    t = 0.0;
                    s = clamp01(-c / a);
                } else if (t > 1.0) {
                    t = 1.0;
                    s = clamp01((b - c) / a);
                }
            }
        }
        double dx = p0x + d1x * s - (q0x + d2x * t);
        double dy = p0y + d1y * s - (q0y + d2y * t);
        double dz = p0z + d1z * s - (q0z + d2z * t);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
