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

    public static TickResult tick(WindowState state) {
        int activeBeforeTick = effectiveWindowTicks(state);
        int mightyForceTicks = decrement(state.mightyForceTicks());
        int swiftStepTicks = decrement(state.swiftStepTicks());
        int murderSenseTicks = decrement(state.murderSenseTicks());
        int healingTicks = decrement(state.healingTicks());
        int healingPulseTicks = state.healingPulseTicks();
        boolean healingPulseDue = false;
        if (state.healingTicks() > 0) {
            healingPulseTicks++;
            if (healingPulseTicks >= 20) {
                healingPulseTicks = 0;
                healingPulseDue = true;
            }
        }
        int clairvoyanceSelfTicks = decrement(state.clairvoyanceSelfTicks());
        int clairvoyanceOthersTicks = decrement(state.clairvoyanceOthersTicks());
        WindowState next = new WindowState(
                mightyForceTicks,
                swiftStepTicks,
                murderSenseTicks,
                healingTicks,
                healingPulseTicks,
                clairvoyanceSelfTicks,
                clairvoyanceOthersTicks,
                state.deferredCooldownTicks()
        );
        boolean syncRequired = shouldSyncAfterTick(state.mightyForceTicks(), mightyForceTicks)
                || shouldSyncAfterTick(state.swiftStepTicks(), swiftStepTicks)
                || shouldSyncAfterTick(state.murderSenseTicks(), murderSenseTicks)
                || shouldSyncAfterTick(state.healingTicks(), healingTicks)
                || shouldSyncAfterTick(state.clairvoyanceSelfTicks(), clairvoyanceSelfTicks)
                || shouldSyncAfterTick(state.clairvoyanceOthersTicks(), clairvoyanceOthersTicks);
        return new TickResult(
                next,
                healingPulseDue,
                shouldStartDeferredCooldown(
                        activeBeforeTick,
                        effectiveWindowTicks(next),
                        state.deferredCooldownTicks()
                ),
                syncRequired
        );
    }

    private static int effectiveWindowTicks(WindowState state) {
        return effectiveWindowTicks(
                state.mightyForceTicks(),
                state.swiftStepTicks(),
                state.murderSenseTicks(),
                state.healingTicks(),
                state.clairvoyanceSelfTicks(),
                state.clairvoyanceOthersTicks()
        );
    }

    private static int decrement(int ticks) {
        return ticks > 0 ? ticks - 1 : 0;
    }

    private static boolean shouldSyncAfterTick(int before, int after) {
        return before > 0 && (after == 0 || after % 20 == 0);
    }

    /**
     * Immutable component snapshot used by the Apprentice-owned runtime tick.
     * 预备魔女运行时 tick 使用的不可变组件状态快照。
     */
    public record WindowState(
            int mightyForceTicks,
            int swiftStepTicks,
            int murderSenseTicks,
            int healingTicks,
            int healingPulseTicks,
            int clairvoyanceSelfTicks,
            int clairvoyanceOthersTicks,
            int deferredCooldownTicks
    ) {
    }

    public record TickResult(
            WindowState state,
            boolean healingPulseDue,
            boolean startDeferredCooldown,
            boolean syncRequired
    ) {
    }
}
