package dev.caecorthus.sparkwitch.roles.civilian.bear.smoke;

import it.unimi.dsi.fastutil.HashCommon;

/**
 * Seeded low-frequency 3D value noise that makes every cloud's outline lumpy: two octaves of smoothstep-interpolated
 * lattice values (lumps about {@link #LUMP_CELLS} cells apart, plus a finer half-weight layer), in -1..1, sampled at
 * cell offsets from the seed. Pure and deterministic; only the server evaluates it, once per burst.
 * 带种子的低频三维值噪声，使每团烟的轮廓都凹凸不平：两层平滑插值的格点值（大块起伏间距约 {@link #LUMP_CELLS} 格，外加一层
 * 权重较小的细节），取值 -1..1，在相对起点的格偏移处采样。纯函数且确定；只有服务端在每次爆开时算一次。
 */
final class SmokeShapeNoise {
    /** Spacing of the large lumps in cells (2.5 blocks, a few lumps across a cloud). / 大块起伏的间距（格，2.5 方块，一团烟上有几块）。 */
    static final double LUMP_CELLS = 5.0;
    private static final double COARSE_WEIGHT = 0.7;
    private static final double FINE_WEIGHT = 0.3;
    private static final long FINE_SALT = 0x9E3779B97F4A7C15L;

    private final long seed;

    SmokeShapeNoise(long seed) {
        this.seed = seed;
    }

    /** Noise -1..1 at a cell offset. / 某格偏移处的噪声 -1..1。 */
    double at(double x, double y, double z) {
        double coarse = octave(seed, x / LUMP_CELLS, y / LUMP_CELLS, z / LUMP_CELLS);
        double fine = octave(seed ^ FINE_SALT, x * 2.0 / LUMP_CELLS, y * 2.0 / LUMP_CELLS, z * 2.0 / LUMP_CELLS);
        return Math.max(-1.0, Math.min(1.0, COARSE_WEIGHT * coarse + FINE_WEIGHT * fine));
    }

    private static double octave(long seed, double x, double y, double z) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        int iz = (int) Math.floor(z);
        double u = smooth(x - ix);
        double v = smooth(y - iy);
        double w = smooth(z - iz);
        double x00 = lerp(u, lattice(seed, ix, iy, iz), lattice(seed, ix + 1, iy, iz));
        double x10 = lerp(u, lattice(seed, ix, iy + 1, iz), lattice(seed, ix + 1, iy + 1, iz));
        double x01 = lerp(u, lattice(seed, ix, iy, iz + 1), lattice(seed, ix + 1, iy, iz + 1));
        double x11 = lerp(u, lattice(seed, ix, iy + 1, iz + 1), lattice(seed, ix + 1, iy + 1, iz + 1));
        return lerp(w, lerp(v, x00, x10), lerp(v, x01, x11));
    }

    /** Hashed lattice value -1..1. / 哈希得到的格点值 -1..1。 */
    private static double lattice(long seed, int x, int y, int z) {
        long h = HashCommon.mix(seed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L);
        return (h >>> 40) / (double) (1L << 23) - 1.0;
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double t, double a, double b) {
        return a + (b - a) * t;
    }
}
