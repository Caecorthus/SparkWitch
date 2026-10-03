package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.effect;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerRules;

/**
 * Pure GW-MR deduction math. Wathe balances have no floor, so the shell takes at most what the target holds and never
 * pushes a balance below zero (D7); a zero or negative balance loses nothing.
 * GW-MR 扣款的纯计算。Wathe 余额没有下限，因此炮弹最多扣掉目标现有的钱，绝不把余额扣成负数（D7）；
 * 余额为零或负数时不扣任何钱。
 */
final class MrShellEffectRules {
    private MrShellEffectRules() {
    }

    /** Coins removed from one target. / 从单个目标扣除的金币。 */
    static int amount(double factor, int balance) {
        int nominal = (int) Math.round(PotionGunnerRules.MR_MAX_DEDUCTION * unit(factor));
        return Math.max(0, Math.min(nominal, Math.max(0, balance)));
    }

    private static double unit(double factor) {
        return Double.isNaN(factor) ? 0.0 : Math.max(0.0, Math.min(1.0, factor));
    }
}
