package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.effect;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerRules;

/**
 * Pure GW-AC math: the penalty added to one cooldown is {@code ceil(nominal × AC_FRACTION × factor)} ticks, rounded
 * up like SparkTraits' non-final-kill 20% rule, so even a short cooldown at the outer ring gains at least one tick.
 * 纯 GW-AC 计算：对单项冷却追加的惩罚为 {@code ceil(标准冷却 × AC_FRACTION × 衰减)} 刻，与 SparkTraits 的 20% 规则一样向上取整，
 * 因此外环的短冷却也至少增加 1 刻。
 */
public final class AcShellEffectRules {
    private AcShellEffectRules() {
    }

    public static int penaltyTicks(int nominalTicks, double factor) {
        if (nominalTicks <= 0 || !(factor > 0.0)) {
            return 0;
        }
        return (int) Math.ceil(nominalTicks * PotionGunnerRules.AC_FRACTION * Math.min(1.0, factor));
    }
}
