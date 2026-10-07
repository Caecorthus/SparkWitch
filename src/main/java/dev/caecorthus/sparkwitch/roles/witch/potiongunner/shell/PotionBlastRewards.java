package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerRules;

import java.util.List;

/**
 * Pure per-shell hit reward: {@link PotionGunnerRules#HIT_REWARD} for every caught player that is neither the gunner
 * nor an ally, paid once per shell, and only while the gunner is online, alive, playing (not spectating, or inside a
 * Rift Gate) and still exactly the Potion Gunner (no posthumous pay, as with the Time Stealer's stamps).
 * 纯计算的每颗炮弹命中奖励：每名被波及且既非药炮手本人也非己方的玩家计 {@link PotionGunnerRules#HIT_REWARD}，
 * 每颗炮弹结算一次；仅在药炮手在线、存活、仍在对局中（非旁观，或位于裂隙门内）且仍精确为药炮手时发放
 * （与窃时者印章一样没有身后奖励）。
 */
public final class PotionBlastRewards {
    private PotionBlastRewards() {
    }

    /** Hits that count for the reward. / 计入奖励的命中数。 */
    public static int rewardedHits(List<PotionBlastHit> hits) {
        int count = 0;
        for (PotionBlastHit hit : hits) {
            if (hit.rewarded()) {
                count++;
            }
        }
        return count;
    }

    /**
     * The "not spectating" part of eligibility. A Rift Gate occupant is an ALIVE spectator (Riftwalker D3), not a dead
     * one, so a gunner that is inside a gate when its shell lands is still paid (owner decision 2026-10-04); any other
     * spectator is not. Wathe liveness is checked separately, so a Wathe-dead spectator never passes.
     * 资格中的「非旁观」部分。裂隙门内的玩家是存活旁观者（隙行者 D3）而非死者，因此炮弹落地时位于门内的药炮手照常领取奖励
     * （所有者 2026-10-04 决定）；其他旁观者不领取。Wathe 存活另行校验，因此 Wathe 判定死亡的旁观者永远不通过。
     */
    public static boolean notSpectating(boolean spectator, boolean insideRiftGate) {
        return !spectator || insideRiftGate;
    }

    /** Gold to pay; 0 when the gunner is not eligible or nothing counted. / 应发金币；不合格或无计数时为 0。 */
    public static int amount(boolean gunnerOnline, boolean gunnerAliveAndPlaying, boolean stillPotionGunner,
                             int rewardedHits) {
        if (!gunnerOnline || !gunnerAliveAndPlaying || !stillPotionGunner || rewardedHits <= 0) {
            return 0;
        }
        return PotionGunnerRules.HIT_REWARD * rewardedHits;
    }
}
