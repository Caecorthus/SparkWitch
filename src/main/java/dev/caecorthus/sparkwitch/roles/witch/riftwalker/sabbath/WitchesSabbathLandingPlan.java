package dev.caecorthus.sparkwitch.roles.witch.riftwalker.sabbath;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.BiFunction;

/**
 * Pure landing geometry of the Witches' Sabbath (research/04 §2): the candidate ring order, the floor snap, the
 * one-spot-per-teammate assignment and the arrival yaw. World validation is injected ({@link SpotFinder}), so the
 * search and the "never overlap another arrival" rule are unit-testable without a world.
 * 魔女集会的纯落点几何（research/04 §2）：候选圆环顺序、地面吸附、每名队友一个落点的分配，以及到达朝向。
 * 世界校验通过 {@link SpotFinder} 注入，因此搜索与「到达点互不重叠」规则无需世界即可单元测试。
 */
public final class WitchesSabbathLandingPlan {
    private static final double DIAGONAL = Math.sqrt(0.5);
    /** Compass unit offsets: N, E, S, W, then NE, SE, SW, NW (Minecraft: north = −z). / 罗盘单位偏移：北东南西，再东北、东南、西南、西北。 */
    private static final double[][] COMPASS = {
            {0, -1}, {1, 0}, {0, 1}, {-1, 0},
            {DIAGONAL, -DIAGONAL}, {DIAGONAL, DIAGONAL}, {-DIAGONAL, DIAGONAL}, {-DIAGONAL, -DIAGONAL}
    };

    private WitchesSabbathLandingPlan() {
    }

    /** One planned arrival. / 一个已规划的到达点。 */
    public record Landing<T>(T target, Vec3d feet, Box body) {
    }

    /**
     * Validates one horizontal candidate for one teammate and returns the snapped, safe feet position (or null). It
     * must not consider other planned arrivals; {@link #plan} does that.
     * 为某名队友校验一个水平候选点，返回吸附后的安全脚底位置（或 null）。不得考虑其他已规划的到达点，由 {@link #plan} 处理。
     */
    @FunctionalInterface
    public interface SpotFinder<T> {
        @Nullable Vec3d find(T target, Vec3d candidate);
    }

    /**
     * Candidate feet positions around {@code feet}: every ring of {@link WitchesSabbathRules#RING_RADII} (inner first),
     * eight compass points each, all at the caster's feet height (the finder snaps to the real floor).
     * {@code feet} 周围的候选脚底位置：按 {@link WitchesSabbathRules#RING_RADII} 由内向外，每圈八个罗盘方向，高度均为
     * 施放者脚底高度（由校验器吸附到实际地面）。
     */
    public static List<Vec3d> candidates(Vec3d feet) {
        List<Vec3d> result = new ArrayList<>(WitchesSabbathRules.RING_RADII.length * COMPASS.length);
        for (double radius : WitchesSabbathRules.RING_RADII) {
            for (double[] direction : COMPASS) {
                result.add(new Vec3d(feet.x + direction[0] * radius, feet.y, feet.z + direction[1] * radius));
            }
        }
        return List.copyOf(result);
    }

    /**
     * The highest collision top within {@code [feetY − step, feetY + step]}, i.e. the floor a player could step onto
     * at that column; empty when none (no floor near the caster's height).
     * {@code [feetY − step, feetY + step]} 内最高的碰撞顶面，即玩家在该列可踏上的地面；没有时为空（施放者高度附近无地面）。
     */
    public static OptionalDouble snapFloor(double feetY, double[] collisionTops, double step) {
        double best = Double.NaN;
        for (double top : collisionTops) {
            if (Double.isFinite(top) && top >= feetY - step && top <= feetY + step
                    && (Double.isNaN(best) || top > best)) {
                best = top;
            }
        }
        return Double.isNaN(best) ? OptionalDouble.empty() : OptionalDouble.of(best);
    }

    /**
     * Assigns at most one spot per target, in target order then candidate order. A candidate is skipped once taken
     * or when the target's body there would overlap an already planned arrival; the (expensive) finder runs last.
     * Targets without a spot are simply left out.
     * 按目标顺序、再按候选顺序为每个目标分配至多一个落点。已被占用、或该目标在此处的身体会与已规划到达点重叠的候选点被跳过；
     * （昂贵的）校验器最后运行。找不到落点的目标直接略过。
     */
    public static <T> List<Landing<T>> plan(List<T> targets, List<Vec3d> candidates, SpotFinder<T> finder,
                                            BiFunction<T, Vec3d, Box> standingBoxAt) {
        List<Landing<T>> landings = new ArrayList<>();
        boolean[] taken = new boolean[candidates.size()];
        for (T target : targets) {
            for (int i = 0; i < candidates.size(); i++) {
                if (taken[i] || overlapsPlanned(standingBoxAt.apply(target, candidates.get(i)), landings)) {
                    continue;
                }
                Vec3d feet = finder.find(target, candidates.get(i));
                if (feet == null) {
                    continue;
                }
                Box body = standingBoxAt.apply(target, feet);
                if (overlapsPlanned(body, landings)) {
                    continue;
                }
                taken[i] = true;
                landings.add(new Landing<>(target, feet, body));
                break;
            }
        }
        return List.copyOf(landings);
    }

    /**
     * Yaw (degrees, Minecraft convention: 0 = south/+z, 90 = west/−x) that looks from {@code from} toward
     * {@code to}; 0 when they share a column.
     * 从 {@code from} 看向 {@code to} 的偏航角（度，Minecraft 约定：0 = 南/+z，90 = 西/−x）；同一列时为 0。
     */
    public static float yawToward(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        if (dx * dx + dz * dz < 1.0E-8) {
            return 0.0F;
        }
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        yaw %= 360.0F;
        if (yaw >= 180.0F) {
            yaw -= 360.0F;
        } else if (yaw < -180.0F) {
            yaw += 360.0F;
        }
        return yaw;
    }

    /**
     * Finite body inside the world height and, when known, wholly inside Wathe's play area (below {@code minY} Wathe
     * kills the player as fallen out of the train).
     * 有限的身体位于世界高度内，并在已知时完全位于 Wathe 游戏区域内（低于 {@code minY} 会被 Wathe 判为掉出列车而死亡）。
     */
    public static boolean insideBounds(Box body, int bottomY, int topY, @Nullable Box playArea) {
        return Double.isFinite(body.minX) && Double.isFinite(body.maxX)
                && Double.isFinite(body.minY) && Double.isFinite(body.maxY)
                && Double.isFinite(body.minZ) && Double.isFinite(body.maxZ)
                && body.minY >= bottomY && body.maxY <= topY
                && (playArea == null || body.minX >= playArea.minX && body.maxX <= playArea.maxX
                && body.minY >= playArea.minY && body.maxY <= playArea.maxY
                && body.minZ >= playArea.minZ && body.maxZ <= playArea.maxZ);
    }

    private static <T> boolean overlapsPlanned(Box body, List<Landing<T>> landings) {
        for (Landing<T> landing : landings) {
            if (landing.body().intersects(body)) {
                return true;
            }
        }
        return false;
    }
}
