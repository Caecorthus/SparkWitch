package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerRules;

import java.util.List;

/**
 * Pure per-shell hit reward: {@link PotionGunnerRules#HIT_REWARD} for every caught player that is neither the gunner
 * nor an ally, paid once per shell, and only while the gunner is online, alive, playing and still exactly the Potion
 * Gunner (no posthumous pay, as with the Time Stealer's stamps).
 * 纯计算的每颗炮弹命中奖励：每名被波及且既非药炮手本人也非己方的玩家计 {@link PotionGunnerRules#HIT_REWARD}，
 * 每颗炮弹结算一次；仅在药炮手在线、存活、仍在对局中且仍精确为药炮手时发放（与窃时者印章一样没有身后奖励）。
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

    /** Gold to pay; 0 when the gunner is not eligible or nothing counted. / 应发金币；不合格或无计数时为 0。 */
    public static int amount(boolean gunnerOnline, boolean gunnerAliveAndPlaying, boolean stillPotionGunner,
                             int rewardedHits) {
        if (!gunnerOnline || !gunnerAliveAndPlaying || !stillPotionGunner || rewardedHits <= 0) {
            return 0;
        }
        return PotionGunnerRules.HIT_REWARD * rewardedHits;
    }
}
