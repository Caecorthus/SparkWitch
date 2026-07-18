package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

/** Strict gate that leaves unmarked and non-cocktail consumption untouched. */
public final class PoisonAppleDrinkRules {
    private PoisonAppleDrinkRules() {
    }

    public static boolean shouldApply(boolean cocktail, boolean markedByPoisonApple) {
        return cocktail && markedByPoisonApple;
    }
}
