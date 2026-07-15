package dev.caecorthus.sparkwitch.roles.civilian.orthopedist;

/**
 * Pure stamina-delta policy shared by the Wathe stamina mixin and focused tests.
 * Wathe 体力混入与聚焦测试共用的纯消耗差值规则。
 */
public final class OrthopedistStaminaRules {
    private OrthopedistStaminaRules() {
    }

    public static float adjustedValue(
            float currentValue,
            float requestedValue,
            boolean reductionActive,
            boolean hunterInjuryControlsMovement
    ) {
        if (!reductionActive || hunterInjuryControlsMovement || requestedValue >= currentValue) {
            return requestedValue;
        }
        float requestedConsumption = currentValue - requestedValue;
        return currentValue - requestedConsumption * (float) OrthopedistRules.STAMINA_CONSUMPTION_MULTIPLIER;
    }
}
