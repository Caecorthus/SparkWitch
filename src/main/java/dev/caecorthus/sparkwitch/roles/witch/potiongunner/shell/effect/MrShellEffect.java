package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.effect;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionBlastContext;

/**
 * GW-MR: gold deduction scaled by falloff, clamped at a zero balance.
 * GW-MR：按衰减扣除金币，余额不低于 0。
 */
public final class MrShellEffect {
    private MrShellEffect() {
    }

    public static void apply(PotionBlastContext context) {
        // Implemented by its work package. / 由对应工作包实现。
    }
}
