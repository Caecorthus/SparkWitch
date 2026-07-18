package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

/** Pure server validation rules for the one-use Vendetta knife. / 仇杀客一次性复仇刀的纯服务端校验规则。 */
public final class VendettaKnifeRules {
    public static final int MINIMUM_HOLD_TICKS = 10;
    public static final double MAX_RANGE = 3.0D;

    private VendettaKnifeRules() {
    }

    public static boolean canAttempt(
            boolean activeVendetta,
            boolean knifeAvailable,
            boolean holdingKnife,
            boolean boundTarget,
            boolean targetAlive,
            int heldTicks,
            double squaredDistance,
            boolean hasLineOfSight
    ) {
        return activeVendetta
                && knifeAvailable
                && holdingKnife
                && boundTarget
                && targetAlive
                && heldTicks >= MINIMUM_HOLD_TICKS
                && Double.isFinite(squaredDistance)
                && squaredDistance <= MAX_RANGE * MAX_RANGE
                && hasLineOfSight;
    }

    public static boolean confirmedDeath(boolean deadBefore, boolean deadAfter) {
        return !deadBefore && deadAfter;
    }
}
