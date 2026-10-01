package dev.caecorthus.sparkwitch.roles.civilian.fisher.swordfish;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.FisherRules;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/** Pure charge, acceptance and effective-faction consequences. / 纯蓄力、命中接纳与有效阵营后果规则。 */
public final class SwordfishRules {
    public static final int MAX_USE_TICKS = 100;
    public static final long RELEASE_VALID_TICKS = 5L;
    public static final int SERVER_MIN_HOLD_TICKS = FisherRules.SWORDFISH_MIN_HOLD_TICKS - 2;
    /** Quarter-block aim slack (about 4.8 degrees at 3 blocks) tolerates a late rotation without extending reach.
     * 四分之一格瞄准容差（三格处约 4.8 度），容忍朝向迟到但不延长射线。 */
    public static final double AIM_TOLERANCE = 0.25;

    private SwordfishRules() {
    }

    public static boolean qualifiedHold(int heldTicks) {
        return heldTicks >= FisherRules.SWORDFISH_MIN_HOLD_TICKS && heldTicks <= MAX_USE_TICKS;
    }

    /** The client still holds 11 ticks; server use/release packets may straddle two fewer tick boundaries.
     * 客户端仍需 11 tick；服务端使用/松手包可能少跨过两次 tick 边界。 */
    public static boolean qualifiedServerHold(int heldTicks) {
        return heldTicks >= SERVER_MIN_HOLD_TICKS && heldTicks <= MAX_USE_TICKS;
    }

    public static boolean qualifiedRelease(int heldTicks, long ageTicks, boolean sameWorld, boolean sameStack) {
        return qualifiedServerHold(heldTicks) && ageTicks >= 0 && ageTicks <= RELEASE_VALID_TICKS
                && sameWorld && sameStack;
    }

    /** Box entry on the same block-clipped aim segment; a miss never falls back to proximity.
     * 在同一条方块截断的瞄准线段上求箱体入射点；射线未命中时绝不退回距离判断。 */
    static double entryDistanceSquared(Vec3d start, Vec3d end, Box box) {
        return box.contains(start) ? 0.0 : box.raycast(start, end).map(start::squaredDistanceTo).orElse(-1.0);
    }

    /** Earlier candidates retain exact ties; callers list players before devices.
     * 完全等距时保留先列出的候选；调用方将玩家放在设备之前。 */
    static <T> @Nullable T nearestOnRay(Vec3d start, Vec3d end, Iterable<T> candidates, Function<T, Box> boxOf) {
        T nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (T candidate : candidates) {
            double entry = entryDistanceSquared(start, end, boxOf.apply(candidate));
            if (entry >= 0.0 && entry < distance) {
                nearest = candidate;
                distance = entry;
            }
        }
        return nearest;
    }

    /** Native knife distance is feet-to-feet, inclusive at three blocks. / 原刀按脚下位置计算距离，包含三格边界。 */
    public static boolean inReach(double squaredDistance) {
        return Double.isFinite(squaredDistance) && squaredDistance >= 0.0
                && squaredDistance <= FisherRules.SWORDFISH_REACH * FisherRules.SWORDFISH_REACH;
    }

    public static boolean acceptsPlayerHit(boolean livingParticipant, boolean samePlayer,
                                            double squaredDistance, boolean lineOfSight) {
        return livingParticipant && !samePlayer && inReach(squaredDistance) && lineOfSight;
    }

    /** Factions are pre-hit snapshots; intercepted or fake deaths never trigger friendly-fire punishment.
     * 阵营来自命中前快照；被拦截的死亡或假死永不触发小脑。 */
    public static boolean shouldPunish(Identifier attackerFaction, Identifier victimFaction,
                                       boolean deadBefore, boolean deadAfter, boolean intercepted, boolean nonFinal) {
        return FactionIds.CIVILIAN.equals(attackerFaction) && FactionIds.CIVILIAN.equals(victimFaction)
                && !deadBefore && deadAfter && !intercepted && !nonFinal;
    }
}
