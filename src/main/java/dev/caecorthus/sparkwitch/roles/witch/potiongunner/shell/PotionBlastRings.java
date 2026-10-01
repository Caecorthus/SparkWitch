package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell;

/**
 * Pure blast-cube geometry: membership in the N×N×N cube and the ring falloff (owner default D5). Offsets are measured
 * from the blast centre to the target's feet ({@code dx}, {@code dz}) and to its bounding-box bottom/top
 * ({@code dyMin}, {@code dyMax}). Rings use the horizontal Chebyshev distance, so a 5×5 cube has rings 0/1/2 with
 * factors 1, 2/3, 1/3; 7×7 gives 1, 3/4, 1/2, 1/4; 3×3 gives 1, 1/2.
 * 纯爆炸立方体几何：N×N×N 立方体判定与环形衰减（所有者默认 D5）。偏移量从爆心量到目标脚下（{@code dx}、{@code dz}）以及
 * 碰撞箱底部/顶部（{@code dyMin}、{@code dyMax}）。分环使用水平切比雪夫距离：5×5 为 0/1/2 环，系数 1、2/3、1/3；
 * 7×7 为 1、3/4、1/2、1/4；3×3 为 1、1/2。
 */
public final class PotionBlastRings {
    private PotionBlastRings() {
    }

    /** Half the cube edge. / 立方体边长的一半。 */
    public static double half(int size) {
        return size / 2.0;
    }

    /** Outermost ring index, {@code (N − 1) / 2}. / 最外环序号 {@code (N − 1) / 2}。 */
    public static int maxRing(int size) {
        return Math.max(0, (size - 1) / 2);
    }

    /**
     * Feet inside the horizontal square ({@code |dx|, |dz| ≤ N/2}) and the body overlapping the vertical slab
     * {@code [−N/2, N/2]}, so a direct chest hit counts. Non-finite input is outside.
     * 脚下位于水平正方形内（{@code |dx|、|dz| ≤ N/2}），且身体与竖直区间 {@code [−N/2, N/2]} 重叠，因此直接命中胸口也算。
     * 非有限输入视为在外。
     */
    public static boolean inCube(int size, double dx, double dz, double dyMin, double dyMax) {
        if (size <= 0 || !Double.isFinite(dx) || !Double.isFinite(dz) || !Double.isFinite(dyMin)
                || !Double.isFinite(dyMax)) {
            return false;
        }
        double half = half(size);
        return Math.abs(dx) <= half && Math.abs(dz) <= half && dyMin <= half && dyMax >= -half;
    }

    /** Ring of a feet offset: {@code min(maxRing, floor(max(|dx|, |dz|) + 0.5))}. / 脚下偏移所在的环。 */
    public static int ring(int size, double dx, double dz) {
        double distance = Math.max(Math.abs(dx), Math.abs(dz));
        if (!Double.isFinite(distance)) {
            return maxRing(size);
        }
        return Math.min(maxRing(size), (int) Math.floor(distance + 0.5));
    }

    /** Falloff in (0, 1]: {@code 1 − ring / ((N + 1) / 2)}. / 衰减系数，取值 (0, 1]。 */
    public static double factor(int size, int ring) {
        int clamped = Math.max(0, Math.min(maxRing(size), ring));
        return 1.0 - clamped / ((size + 1) / 2.0);
    }
}
