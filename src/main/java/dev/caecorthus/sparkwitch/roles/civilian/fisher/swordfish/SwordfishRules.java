package dev.caecorthus.sparkwitch.roles.civilian.fisher.swordfish;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.FisherRules;
import net.minecraft.util.Identifier;

/** Pure charge, acceptance and effective-faction consequences. / 纯蓄力、命中接纳与有效阵营后果规则。 */
public final class SwordfishRules {
    public static final int MAX_USE_TICKS = 100;
    public static final long RELEASE_VALID_TICKS = 5L;

    private SwordfishRules() {
    }

    public static boolean qualifiedHold(int heldTicks) {
        return heldTicks >= FisherRules.SWORDFISH_MIN_HOLD_TICKS && heldTicks <= MAX_USE_TICKS;
    }

    public static boolean qualifiedRelease(int heldTicks, long ageTicks, boolean sameWorld, boolean sameStack) {
        return qualifiedHold(heldTicks) && ageTicks >= 0 && ageTicks <= RELEASE_VALID_TICKS
                && sameWorld && sameStack;
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
