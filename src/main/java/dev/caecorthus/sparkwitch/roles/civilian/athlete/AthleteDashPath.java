package dev.caecorthus.sparkwitch.roles.civilian.athlete;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Destination search of the dash: moves the player's whole bounding box forward in {@link AthleteRules#DASH_STEP}
 * steps, the way walking would (flat first, else step up at most the step height; a drop deeper than
 * {@link AthleteRules#MAX_STEP_DOWN} is a ledge), and keeps the last box that touches nothing. Every accepted box is
 * collision-free and stands on a floor, so the dash never ends inside a wall, door or panel, never squeezes past a
 * corner a walk cannot pass, and never leaves the player over a drop. World access goes through {@link Space}.
 * 冲刺的落点搜索：以 {@link AthleteRules#DASH_STEP} 为步长向前移动玩家的整个碰撞箱，规则与走路相同（先平移，受阻时
 * 最多跨上一个跨步高度；落差超过 {@link AthleteRules#MAX_STEP_DOWN} 即视为边缘），保留最后一个不与任何东西相交的碰撞箱。
 * 每个被接受的碰撞箱都无碰撞且脚下有地面，因此冲刺不会停在墙、门或面板内部，不会钻过走路过不去的拐角，也不会把玩家
 * 留在悬空处。世界访问经由 {@link Space}。
 */
public final class AthleteDashPath {
    /** Slack added to a fall query so "nothing within the limit" is distinguishable. / 下落查询的余量，用于区分“限度内无地面”。 */
    private static final double FALL_SLACK = 1.0D / 32.0D;

    /** Collision queries the search needs. / 搜索所需的碰撞查询。 */
    public interface Space {
        /** Whether {@code box} collides with nothing (blocks, hard entities, world border). / 碰撞箱是否不与任何东西相交。 */
        boolean isFree(Box box);

        /** How far {@code box} can move straight down, capped at {@code max}. / 碰撞箱可竖直下移的距离，上限为 max。 */
        double fallDistance(Box box, double max);
    }

    private AthleteDashPath() {
    }

    /**
     * Feet position of the farthest reachable spot along {@code direction} (horizontal unit vector), or {@code null}
     * when the start has no floor within {@link AthleteRules#MAX_START_DROP} or the dash would move less than
     * {@link AthleteRules#MIN_DASH_DISTANCE}.
     * 沿 direction（水平单位向量）可到达的最远位置的脚部坐标；起点在 {@link AthleteRules#MAX_START_DROP} 内没有地面，或
     * 冲刺距离不足 {@link AthleteRules#MIN_DASH_DISTANCE} 时返回 null。
     */
    public static @Nullable Vec3d find(Space space, Box startBox, Vec3d startFeet, Vec3d direction, double stepUp) {
        // An airborne start (mid-jump) dashes along the floor it would land on.
        // 起跳中的玩家沿其将要落到的地面冲刺。
        double startFall = space.fallDistance(startBox, AthleteRules.MAX_START_DROP + FALL_SLACK);
        if (startFall > AthleteRules.MAX_START_DROP) {
            return null;
        }
        double y = -startFall;
        double previousX = 0.0D;
        double previousZ = 0.0D;
        double reached = 0.0D;
        int steps = (int) Math.ceil(AthleteRules.DASH_DISTANCE / AthleteRules.DASH_STEP);
        for (int i = 1; i <= steps; i++) {
            double distance = Math.min(i * AthleteRules.DASH_STEP, AthleteRules.DASH_DISTANCE);
            double x = direction.x * distance;
            double z = direction.z * distance;
            Box flat = startBox.offset(x, y, z);
            double nextY;
            if (space.isFree(flat)) {
                double fall = space.fallDistance(flat, AthleteRules.MAX_STEP_DOWN + FALL_SLACK);
                if (fall > AthleteRules.MAX_STEP_DOWN) {
                    break;
                }
                nextY = y - fall;
            } else {
                // Step up like walking: rise in place, move, then settle onto whatever is underneath.
                // 与走路一样跨步：先原地升高，再前移，然后落到下方的东西上。
                if (stepUp <= 0.0D
                        || !space.isFree(startBox.offset(previousX, y + stepUp, previousZ))
                        || !space.isFree(startBox.offset(x, y + stepUp, z))) {
                    break;
                }
                nextY = y + stepUp - space.fallDistance(startBox.offset(x, y + stepUp, z), stepUp);
            }
            y = nextY;
            previousX = x;
            previousZ = z;
            reached = distance;
        }
        if (reached < AthleteRules.MIN_DASH_DISTANCE) {
            return null;
        }
        return startFeet.add(previousX, y, previousZ);
    }
}
