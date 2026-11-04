package dev.caecorthus.sparkwitch.roles.civilian.athlete;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Destination search of the dash: moves the player's whole bounding box forward in {@link AthleteRules#DASH_STEP}
 * steps the way walking would — flat first, else the smallest step up (in {@link AthleteRules#STEP_RISE} increments,
 * at most the step height) that clears, a drop deeper than {@link AthleteRules#MAX_STEP_DOWN} is a ledge, and a dash
 * aimed close to a block axis slides along a wall on that axis. Every accepted box is collision-free and stands on a
 * floor; the destination is the farthest one whose four corners all have floor within the step-down limit, so the
 * dash never ends inside a wall, door or panel, never squeezes past a corner a walk cannot pass, and never leaves the
 * player hanging over a ledge. World access goes through {@link Space}.
 * 冲刺的落点搜索：以 {@link AthleteRules#DASH_STEP} 为步长、按走路的规则前移玩家的整个碰撞箱——先平移，受阻时以
 * {@link AthleteRules#STEP_RISE} 为增量取能通过的最小跨步高度（不超过跨步高度）；落差超过
 * {@link AthleteRules#MAX_STEP_DOWN} 即为边缘；朝向接近方块轴线的冲刺会沿该轴贴墙滑行。每个被接受的碰撞箱都无碰撞且
 * 脚下有地面；落点取四个角下方在下台阶限度内都有地面的最远位置，因此冲刺不会停在墙、门或面板内部，不会钻过走路过
 * 不去的拐角，也不会把玩家悬在边缘外。世界访问经由 {@link Space}。
 */
public final class AthleteDashPath {
    /** Side and inset of the corner columns probed for floor under the final box. / 最终碰撞箱四角下方探测柱的边长与内缩。 */
    private static final double CORNER = 0.02D;
    private static final double CORNER_INSET = 0.005D;

    /** Collision queries the search needs. / 搜索所需的碰撞查询。 */
    public interface Space {
        /** Whether {@code box} collides with nothing (blocks, hard entities, world border). / 碰撞箱是否不与任何东西相交。 */
        boolean isFree(Box box);

        /** How far {@code box} can move straight down, capped at {@code max}. / 碰撞箱可竖直下移的距离，上限为 max。 */
        double fallDistance(Box box, double max);
    }

    /** One accepted move: the new offset from the start, or a ledge. / 一次被接受的移动：相对起点的新偏移，或边缘。 */
    private record Move(double x, double y, double z, boolean ledge) {
        static final Move LEDGE = new Move(0.0D, 0.0D, 0.0D, true);
    }

    private AthleteDashPath() {
    }

    /**
     * Feet position of the farthest reachable spot along {@code direction} (horizontal unit vector), or {@code null}
     * when the start has no floor within {@link AthleteRules#MAX_START_DROP} or no safe spot lies at least
     * {@link AthleteRules#MIN_DASH_DISTANCE} along the path.
     * 沿 direction（水平单位向量）可到达的最远位置的脚部坐标；起点在 {@link AthleteRules#MAX_START_DROP} 内没有地面，或
     * 路径上至少 {@link AthleteRules#MIN_DASH_DISTANCE} 处没有安全位置时返回 null。
     */
    public static @Nullable Vec3d find(Space space, Box startBox, Vec3d startFeet, Vec3d direction, double stepUp) {
        // An airborne start (mid-jump) dashes along the floor it would land on.
        // 起跳中的玩家沿其将要落到的地面冲刺。
        double startFall = space.fallDistance(startBox, AthleteRules.MAX_START_DROP + AthleteRules.FALL_SLACK);
        if (startFall > AthleteRules.MAX_START_DROP) {
            return null;
        }
        Vec3d slide = slideAxis(direction);
        int steps = (int) Math.ceil(AthleteRules.DASH_DISTANCE / AthleteRules.DASH_STEP);
        double[] xs = new double[steps];
        double[] ys = new double[steps];
        double[] zs = new double[steps];
        double[] lengths = new double[steps];
        int count = 0;
        double x = 0.0D;
        double y = -startFall;
        double z = 0.0D;
        double travelled = 0.0D;
        while (count < steps) {
            double length = Math.min(AthleteRules.DASH_STEP, AthleteRules.DASH_DISTANCE - travelled);
            if (length <= 1.0E-9D) {
                break;
            }
            Move move = move(space, startBox, x, y, z, direction.x * length, direction.z * length, stepUp);
            if (move == null && slide != null) {
                move = move(space, startBox, x, y, z, slide.x * length, slide.z * length, stepUp);
            }
            if (move == null || move.ledge()) {
                break;
            }
            x = move.x();
            y = move.y();
            z = move.z();
            travelled += length;
            xs[count] = x;
            ys[count] = y;
            zs[count] = z;
            lengths[count] = travelled;
            count++;
        }
        for (int i = count - 1; i >= 0 && lengths[i] >= AthleteRules.MIN_DASH_DISTANCE; i--) {
            if (floorUnderEveryCorner(space, startBox.offset(xs[i], ys[i], zs[i]))) {
                return startFeet.add(xs[i], ys[i], zs[i]);
            }
        }
        return null;
    }

    /**
     * Moves the box from offset (x, y, z) by (dx, dz): flat if free (settling down at most the step-down limit, else a
     * ledge), otherwise rising in place by the smallest increment that clears the target, like walking; {@code null}
     * when blocked.
     * 将碰撞箱从偏移 (x, y, z) 移动 (dx, dz)：平移无碰撞时直接移动（下落不超过下台阶限度，否则为边缘），否则像走路一样
     * 原地按能通过目标的最小增量升高；受阻时返回 {@code null}。
     */
    private static @Nullable Move move(Space space, Box startBox, double x, double y, double z,
                                       double dx, double dz, double stepUp) {
        double nx = x + dx;
        double nz = z + dz;
        Box flat = startBox.offset(nx, y, nz);
        if (space.isFree(flat)) {
            double fall = space.fallDistance(flat, AthleteRules.MAX_STEP_DOWN + AthleteRules.FALL_SLACK);
            return fall > AthleteRules.MAX_STEP_DOWN ? Move.LEDGE : new Move(nx, y - fall, nz, false);
        }
        for (int k = 1; stepUp > 0.0D; k++) {
            double up = Math.min(k * AthleteRules.STEP_RISE, stepUp);
            if (!space.isFree(startBox.offset(x, y + up, z))) {
                return null;
            }
            Box raised = startBox.offset(nx, y + up, nz);
            if (space.isFree(raised)) {
                return new Move(nx, y + up - space.fallDistance(raised, up), nz, false);
            }
            if (up >= stepUp) {
                break;
            }
        }
        return null;
    }

    /** The block axis to slide along, or {@code null} when the dash is not aimed close to one. / 贴墙滑行的方块轴线。 */
    private static @Nullable Vec3d slideAxis(Vec3d direction) {
        double ax = Math.abs(direction.x);
        double az = Math.abs(direction.z);
        if (Math.max(ax, az) < AthleteRules.SLIDE_MIN_AXIS_SHARE) {
            return null;
        }
        return ax >= az ? new Vec3d(Math.signum(direction.x), 0.0D, 0.0D)
                : new Vec3d(0.0D, 0.0D, Math.signum(direction.z));
    }

    /**
     * Whether each bottom corner of {@code box} has floor within the step-down limit, so the player is not left
     * hanging over a ledge by a sliver.
     * 碰撞箱底部每个角下方是否都在下台阶限度内有地面，避免玩家只靠一丝边缘悬在外面。
     */
    private static boolean floorUnderEveryCorner(Space space, Box box) {
        double[] xs = {box.minX + CORNER_INSET, box.maxX - CORNER_INSET - CORNER};
        double[] zs = {box.minZ + CORNER_INSET, box.maxZ - CORNER_INSET - CORNER};
        for (double cx : xs) {
            for (double cz : zs) {
                Box column = new Box(cx, box.minY, cz, cx + CORNER, box.minY + CORNER, cz + CORNER);
                if (space.fallDistance(column, AthleteRules.MAX_STEP_DOWN + AthleteRules.FALL_SLACK)
                        > AthleteRules.MAX_STEP_DOWN) {
                    return false;
                }
            }
        }
        return true;
    }
}
