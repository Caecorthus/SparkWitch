package dev.caecorthus.sparkwitch.client.bear.smoke.render;

import java.util.stream.IntStream;

/**
 * Pure, deterministic, tileable 3D noise for the smoke's edge erosion and domain warp, generated once off the render
 * thread. Voxels are RGBA8, x fastest: R = Perlin-Worley (low-frequency shape), G = Worley fBm (detail puffs),
 * B and A = two independent Perlin fBm channels (warp X and Z; warp Y reuses R). Every channel wraps exactly at the
 * tile edge, so the GL texture can use {@code GL_REPEAT} without seams.
 * 纯函数、确定性、可平铺的 3D 噪声，用于烟雾边缘侵蚀与域扭曲，只在渲染线程之外生成一次。体素为 RGBA8，x 变化最快：
 * R = Perlin-Worley（低频形状），G = Worley fBm（细节团块），B 与 A = 两个独立的 Perlin fBm 通道（X、Z 方向扭曲；
 * Y 方向复用 R）。每个通道在平铺边缘严格首尾相接，因此 GL 纹理可以无缝使用 {@code GL_REPEAT}。
 */
public final class SmokeNoise {
    /** Edge of the generated tile, in voxels. / 生成的平铺边长（体素）。 */
    public static final int SIZE = 64;
    public static final long SEED = 0x5E0CE5B0A2L;
    private static final int[][] GRADIENTS = {
            {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
            {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
            {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}
    };

    private SmokeNoise() {
    }

    /**
     * Generates a {@code size}³ RGBA8 tile ({@code size * size * size * 4} bytes).
     * 生成边长 {@code size} 的 RGBA8 平铺（{@code size * size * size * 4} 字节）。
     */
    public static byte[] generate(int size, long seed) {
        if (size <= 0 || size > 256) {
            throw new IllegalArgumentException("bad noise size " + size);
        }
        byte[] voxels = new byte[size * size * size * 4];
        int s = (int) seed ^ (int) (seed >>> 32);
        IntStream.range(0, size).parallel().forEach(z -> {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float u = (x + 0.5F) / size;
                    float v = (y + 0.5F) / size;
                    float w = (z + 0.5F) / size;
                    int index = (x + size * (y + size * z)) * 4;
                    voxels[index] = encode(perlinWorley(u, v, w, s));
                    voxels[index + 1] = encode(worleyFbm(u, v, w, 8, s + 101));
                    voxels[index + 2] = encode(perlinFbm(u, v, w, 4, s + 211));
                    voxels[index + 3] = encode(perlinFbm(u, v, w, 4, s + 307));
                }
            }
        });
        return voxels;
    }

    /**
     * Perlin-Worley shape noise at tile coordinates {@code (u, v, w)} in 0..1 (Schneider 2015): billowy Perlin cut by
     * Worley cells. Result 0..1.
     * 平铺坐标 {@code (u, v, w)}（0..1）处的 Perlin-Worley 形状噪声（Schneider 2015）：被 Worley 胞元切开的蓬松 Perlin。
     * 结果 0..1。
     */
    static float perlinWorley(float u, float v, float w, int seed) {
        float perlin = perlinFbm(u, v, w, 4, seed);
        float worley = worleyFbm(u, v, w, 4, seed + 17);
        float lo = worley - 1.0F;
        return clamp01((perlin - lo) / (1.0F - lo));
    }

    /** Three-octave Perlin fBm, 0..1. / 三倍频 Perlin fBm，0..1。 */
    static float perlinFbm(float u, float v, float w, int basePeriod, int seed) {
        float sum = 0.0F;
        float amplitude = 1.0F;
        float total = 0.0F;
        int period = basePeriod;
        for (int octave = 0; octave < 3; octave++) {
            sum += amplitude * perlin(u * period, v * period, w * period, period, seed + octave * 31);
            total += amplitude;
            amplitude *= 0.5F;
            period *= 2;
        }
        return clamp01(sum / total * 0.5F + 0.5F);
    }

    /** Three-octave inverted Worley fBm (1 at cell centres), 0..1. / 三倍频反相 Worley fBm（胞元中心为 1），0..1。 */
    static float worleyFbm(float u, float v, float w, int baseCells, int seed) {
        return 0.625F * worley(u * baseCells, v * baseCells, w * baseCells, baseCells, seed)
                + 0.25F * worley(u * baseCells * 2, v * baseCells * 2, w * baseCells * 2, baseCells * 2, seed + 7)
                + 0.125F * worley(u * baseCells * 4, v * baseCells * 4, w * baseCells * 4, baseCells * 4, seed + 13);
    }

    /**
     * Gradient noise with a lattice that repeats every {@code period} units on each axis, about -1..1.
     * 晶格在各轴上每 {@code period} 个单位重复的梯度噪声，约 -1..1。
     */
    static float perlin(float x, float y, float z, int period, int seed) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);
        float fx = x - x0;
        float fy = y - y0;
        float fz = z - z0;
        float u = fade(fx);
        float v = fade(fy);
        float w = fade(fz);
        float result = 0.0F;
        for (int c = 0; c < 8; c++) {
            int cx = c & 1;
            int cy = (c >> 1) & 1;
            int cz = (c >> 2) & 1;
            int[] g = GRADIENTS[Math.floorMod(hash(wrap(x0 + cx, period), wrap(y0 + cy, period),
                    wrap(z0 + cz, period), seed), GRADIENTS.length)];
            float dot = g[0] * (fx - cx) + g[1] * (fy - cy) + g[2] * (fz - cz);
            float weight = (cx == 1 ? u : 1.0F - u) * (cy == 1 ? v : 1.0F - v) * (cz == 1 ? w : 1.0F - w);
            result += weight * dot;
        }
        return result;
    }

    /**
     * Inverted nearest-feature distance (1 - F1, clamped to 0..1) over {@code cells}³ wrapping cells.
     * {@code cells}³ 个循环胞元上的反相最近特征点距离（1 - F1，钳到 0..1）。
     */
    static float worley(float x, float y, float z, int cells, int seed) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);
        float best = Float.MAX_VALUE;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int cx = x0 + dx;
                    int cy = y0 + dy;
                    int cz = z0 + dz;
                    int h = hash(wrap(cx, cells), wrap(cy, cells), wrap(cz, cells), seed);
                    float px = cx + unit(h);
                    float py = cy + unit(h * 0x2C1B3C6D + 0x9E3779B9);
                    float pz = cz + unit(h * 0x297A2D39 + 0x7F4A7C15);
                    float ddx = x - px;
                    float ddy = y - py;
                    float ddz = z - pz;
                    best = Math.min(best, ddx * ddx + ddy * ddy + ddz * ddz);
                }
            }
        }
        return clamp01(1.0F - (float) Math.sqrt(best));
    }

    private static int wrap(int value, int period) {
        return Math.floorMod(value, period);
    }

    private static int hash(int x, int y, int z, int seed) {
        int h = seed ^ (x * 0x27D4EB2D) ^ (y * 0x165667B1) ^ (z * 0x1B873593);
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return h;
    }

    private static float unit(int h) {
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return (h >>> 8) * (1.0F / (1 << 24));
    }

    private static float fade(float t) {
        return t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
    }

    private static byte encode(float value) {
        return (byte) Math.round(clamp01(value) * 255.0F);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
