package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell;

/**
 * Pure blast-cube geometry: membership in the N×N×N cube and the ring falloff (owner default D5). Membership takes the
 * offsets of the target's bounding-box faces from the blast centre; rings take the offset of its feet
 * ({@code dx}, {@code dz}). Rings use the horizontal Chebyshev distance, so a 5×5 cube has rings 0/1/2 with
 * factors 1, 2/3, 1/3; 7×7 gives 1, 3/4, 1/2, 1/4; 3×3 gives 1, 1/2.
 * 纯爆炸立方体几何：N×N×N 立方体判定与环形衰减（所有者默认 D5）。立方体判定使用目标碰撞箱各面相对爆心的偏移；
 * 分环使用其脚下的偏移（{@code dx}、{@code dz}）。分环使用水平切比雪夫距离：5×5 为 0/1/2 环，系数 1、2/3、1/3；
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
     * Any part of the body in the cube: the box overlaps {@code [−N/2, N/2]} on every axis (offsets of its faces from
     * the blast centre, edges inclusive), so a player whose body visibly stands in the area counts even when the feet
     * point is just outside, and a direct chest hit counts. Non-finite input is outside.
     * 身体任一部分位于立方体内：碰撞箱在每个轴上都与 {@code [−N/2, N/2]} 重叠（各面相对爆心的偏移，边界包含），因此身体
     * 明显站在范围内的玩家即使脚下点略在外也算，直接命中胸口也算。非有限输入视为在外。
     */
    public static boolean inCube(int size, double minDx, double maxDx, double minDz, double maxDz, double dyMin,
                                 double dyMax) {
        if (size <= 0 || !Double.isFinite(minDx) || !Double.isFinite(maxDx) || !Double.isFinite(minDz)
                || !Double.isFinite(maxDz) || !Double.isFinite(dyMin) || !Double.isFinite(dyMax)) {
            return false;
        }
        double half = half(size);
        return overlaps(minDx, maxDx, half) && overlaps(minDz, maxDz, half) && overlaps(dyMin, dyMax, half);
    }

    /** {@code [min, max]} meets {@code [−half, half]}. / 区间与 {@code [−half, half]} 相交。 */
    private static boolean overlaps(double min, double max, double half) {
        return min <= half && max >= -half;
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
