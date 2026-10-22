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
    /**
     * How much nearer (blocks) another candidate may be before a stab submitted on a Partygoer is rejected.
     * 提交给派对客的刺击被拒绝之前，其他候选者最多可以近多少格。
     */
    public static final double PARTYGOER_TIE_MARGIN = 0.3;

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

    /**
     * A SparkStrength Partygoer takes the player rule's reach and sight (owner rule 2026-10-21: it dies in one hit to
     * anything that kills a player); killable replaces the participant check. / SparkStrength 派对客沿用玩家规则的距离与视线
     * （所有者规则 2026-10-21：能杀死玩家的东西一击打死它）；“可击杀”取代参与者判定。
     */
    public static boolean acceptsPartygoerHit(boolean killable, double squaredDistance, boolean lineOfSight) {
        return killable && inReach(squaredDistance) && lineOfSight;
    }

    /** Factions are pre-hit snapshots; intercepted or fake deaths never trigger friendly-fire punishment.
     * 阵营来自命中前快照；被拦截的死亡或假死永不触发小脑。 */
    public static boolean shouldPunish(Identifier attackerFaction, Identifier victimFaction,
                                       boolean deadBefore, boolean deadAfter, boolean intercepted, boolean nonFinal) {
        return FactionIds.CIVILIAN.equals(attackerFaction) && FactionIds.CIVILIAN.equals(victimFaction)
                && !deadBefore && deadAfter && !intercepted && !nonFinal;
    }

    /**
     * A stab submitted on a SparkStrength Partygoer still lands when the nearest candidate on the server ray is at most
     * {@link #PARTYGOER_TIE_MARGIN} blocks nearer (the client's thinner aim box). Arguments are squared entry distances.
     * 提交给 SparkStrength 派对客的刺击在服务端射线上最近的候选者至多近 PARTYGOER_TIE_MARGIN 格时仍然命中（客户端瞄准盒更薄）。
     * 参数为入射距离的平方。
     */
    public static boolean withinPartygoerTie(double submittedSquared, double nearestSquared) {
        return submittedSquared >= 0.0 && nearestSquared >= 0.0
                && Math.sqrt(submittedSquared) - Math.sqrt(nearestSquared) <= PARTYGOER_TIE_MARGIN;
    }
}
