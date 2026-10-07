package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * One player caught by a blast. {@code ring} 0 is the centre ring; {@code factor} is the falloff in (0, 1].
 * {@code ally} marks a witch-faction member and {@code self} the gunner; only TR ever reports either.
 * 被一次爆炸波及的一名玩家。{@code ring} 为 0 表示中心环；{@code factor} 为衰减系数，取值 (0, 1]。
 * {@code ally} 表示魔女阵营成员，{@code self} 表示药炮手本人；只有 TR 会报告这两种。
 */
public record PotionBlastHit(ServerPlayerEntity target, int ring, double factor, boolean ally, boolean self) {
    /** Counts for the +15 hit reward. / 是否计入每人 +15 奖励。 */
    public boolean rewarded() {
        return !ally && !self;
    }
}
