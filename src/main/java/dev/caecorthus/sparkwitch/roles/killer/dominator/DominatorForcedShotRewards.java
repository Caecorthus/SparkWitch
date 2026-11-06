package dev.caecorthus.sparkwitch.roles.killer.dominator;

import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Kill-money rule of the forced shot (owner decision): only the forced shooter's own balance is frozen while the shot
 * and its scheduled consequences run ({@code DominatorForcedShotRewardMixin}); every other player's rewards and
 * penalties (killer teammates' Wathe share and pool, SparkFactionAPI faction rewards, NoellesRoles bounties,
 * SparkStrength penalties) settle normally. The Dominator's own +100 is paid by {@link DominatorForcedShotService}
 * after the shot, on a committed death only.
 * 强制开枪的击杀奖金规则（所有者决定）：只在这一枪及其排定后果运行期间冻结被强制开枪者自己的余额
 * （{@code DominatorForcedShotRewardMixin}）；其他所有玩家的奖励与扣罚（杀手队友的 Wathe 分成与奖池、SparkFactionAPI 阵营奖励、
 * NoellesRoles 悬赏、SparkStrength 扣罚）照常结算。支配者自己的 +100 由 {@link DominatorForcedShotService} 在开枪之后、
 * 且仅在死亡确定时发放。
 */
public final class DominatorForcedShotRewards {
    private DominatorForcedShotRewards() {
    }

    /**
     * True while any forced shot is inside Wathe's receiver (its kills are not the Dominator's own kills).
     * 任意强制开枪正在接收器内时为真（这些击杀不是支配者亲手所杀）。
     */
    public static boolean isSuppressing() {
        return DominatorForcedShotScope.isFiringAny();
    }

    /** Whether {@code owner}'s shop balance must not change now. / 此刻 {@code owner} 的商店余额是否不得变化。 */
    public static boolean freezesBalanceOf(@Nullable PlayerEntity owner) {
        return DominatorForcedShotScope.coversConsequencesOf(owner);
    }
}
