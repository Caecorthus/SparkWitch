package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.effect;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerRules;

/**
 * Pure GW-DK duration math: the centre ring gets the full {@link PotionGunnerRules#DK_MAX_TICKS}, outer rings scale
 * with the falloff factor, and nobody caught gets less than one second.
 * GW-DK 时长的纯计算：中心环获得完整的 {@link PotionGunnerRules#DK_MAX_TICKS}，外环按衰减系数缩放，
 * 任何被波及者至少获得一秒。
 */
final class DkShellEffectRules {
    static final int MIN_TICKS = 20;

    private DkShellEffectRules() {
    }

    /** Blindness and Slowness II length for one hit. / 单次命中的失明与缓慢 II 时长。 */
    static int ticks(double factor) {
        return Math.max(MIN_TICKS, (int) Math.round(PotionGunnerRules.DK_MAX_TICKS * unit(factor)));
    }

    private static double unit(double factor) {
        return Double.isNaN(factor) ? 0.0 : Math.max(0.0, Math.min(1.0, factor));
    }
}
