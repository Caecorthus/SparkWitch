package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkwitch.compat.SparkTraitsWraithBridge;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable state captured at the canonical five-argument kill method HEAD.
 * 在标准五参数击杀方法 HEAD 处保存的不可变状态。
 */
record WraithDeathSnapshot(
        Identifier originalRoleId,
        Identifier originalEffectiveFaction,
        Identifier deathReason,
        WraithTaskSnapshot tasks,
        SparkTraitsWraithBridge.TraitSnapshot traits,
        WraithState.Alignment alignment,
        WraithReturnPoint deathLocation,
        boolean swallowed,
        @Nullable WraithReturnPoint taotieLocation,
        boolean pushedFall,
        long deathGameTime
) {
    boolean usesSwallowedMentalBreakdownFallback(Identifier deathReason) {
        return swallowed && GameConstants.DeathReasons.MENTAL_BREAKDOWN.equals(deathReason);
    }
}
