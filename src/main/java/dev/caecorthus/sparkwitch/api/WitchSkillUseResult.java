package dev.caecorthus.sparkwitch.api;

import org.jetbrains.annotations.Nullable;

public record WitchSkillUseResult(boolean accepted, int cooldownTicks, @Nullable String messageKey) {
    public WitchSkillUseResult {
        cooldownTicks = Math.max(0, cooldownTicks);
    }

    public static WitchSkillUseResult success(int cooldownTicks) {
        return new WitchSkillUseResult(true, cooldownTicks, null);
    }

    public static WitchSkillUseResult success(int cooldownTicks, String messageKey) {
        return new WitchSkillUseResult(true, cooldownTicks, messageKey);
    }

    public static WitchSkillUseResult fail(String messageKey) {
        return new WitchSkillUseResult(false, 0, messageKey);
    }
}
