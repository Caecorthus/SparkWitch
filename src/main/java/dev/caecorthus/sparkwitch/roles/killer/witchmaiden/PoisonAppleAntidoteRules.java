package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

/** Keeps the Poison Apple cure narrower than NoellesRoles' held-item-only native poison hook. */
public final class PoisonAppleAntidoteRules {
    private PoisonAppleAntidoteRules() {
    }

    public static boolean canCure(boolean exactToxicologist, boolean holdsAntidote, boolean coolingDown) {
        return exactToxicologist && holdsAntidote && !coolingDown;
    }
}
