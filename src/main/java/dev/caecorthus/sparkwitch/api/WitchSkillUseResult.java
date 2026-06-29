package dev.caecorthus.sparkwitch.api;

import org.jetbrains.annotations.Nullable;

public record WitchSkillUseResult(
        boolean accepted,
        int cooldownTicks,
        @Nullable String messageKey,
        boolean deferCooldownUntilActiveWindowEnds
) {
    public WitchSkillUseResult {
        cooldownTicks = Math.max(0, cooldownTicks);
    }

    public WitchSkillUseResult(boolean accepted, int cooldownTicks, @Nullable String messageKey) {
        this(accepted, cooldownTicks, messageKey, false);
    }

    public static WitchSkillUseResult success(int cooldownTicks) {
        return new WitchSkillUseResult(true, cooldownTicks, null);
    }

    public static WitchSkillUseResult success(int cooldownTicks, String messageKey) {
        return new WitchSkillUseResult(true, cooldownTicks, messageKey);
    }

    /**
     * Delays cooldown until the active effect window has finished.
     * 将冷却延后到技能有效窗口结束后再开始。
     */
    public static WitchSkillUseResult successAfterActiveWindow(int cooldownTicks, String messageKey) {
        return new WitchSkillUseResult(true, cooldownTicks, messageKey, true);
    }

    public static WitchSkillUseResult fail(String messageKey) {
        return new WitchSkillUseResult(false, 0, messageKey);
    }
}
