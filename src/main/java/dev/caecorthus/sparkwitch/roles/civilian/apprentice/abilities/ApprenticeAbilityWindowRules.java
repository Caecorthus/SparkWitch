package dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities;

/**
 * Pure countdown rules for Apprentice Witch skill windows.
 * 预备魔女技能窗口的纯计时规则，供组件保存状态、单元测试验证边界。
 */
public final class ApprenticeAbilityWindowRules {
    private ApprenticeAbilityWindowRules() {
    }

    public static int effectiveWindowTicks(
            int mightyForceTicks,
            int swiftStepTicks,
            int murderSenseTicks,
            int healingTicks,
            int clairvoyanceSelfTicks,
            int clairvoyanceOthersTicks
    ) {
        // Clairvoyance self-glow is a reveal penalty, not effective skill time.
        // 千里眼的自己发光是暴露代价，不计入技能生效窗口。
        return Math.max(
                Math.max(Math.max(0, mightyForceTicks), Math.max(0, swiftStepTicks)),
                Math.max(
                        Math.max(Math.max(0, murderSenseTicks), Math.max(0, healingTicks)),
                        Math.max(0, clairvoyanceOthersTicks)
                )
        );
    }

    public static boolean shouldStartDeferredCooldown(int activeBeforeTick, int activeAfterTick, int deferredCooldownTicks) {
        return activeBeforeTick > 0 && activeAfterTick <= 0 && deferredCooldownTicks > 0;
    }
}
