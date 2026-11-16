package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Settlement exclusion for the Charon (PR #232, 2026-11-14: "卡戎没复活迷失灵魂时不影响对局结束"): a Charon that has not
 * raised a single Lost Soul is invisible to every "last one standing" count, so the round ends on its own terms without
 * waiting for a neutral that currently has nothing to play for. The moment itself lifts the exclusion: once souls are
 * up, the Charon is the centre of that fight and must be counted like anyone else. Unlike the Stowaway and the
 * untransformed cat this exclusion is conditional, so it cannot use SparkFactionAPI's static, role-id
 * {@code SettlementExclusions}.
 * 卡戎的结算排除（PR #232，2026-11-14：“卡戎没复活迷失灵魂时不影响对局结束”）：一具灵魂都没复活的卡戎对所有“最后存活者”计数
 * 都不可见，因此对局按自己的条件结束，不必等待一个此刻无戏可唱的中立。时刻本身会解除排除：灵魂一旦起身，卡戎就是那场战斗的
 * 核心，必须和其他人一样被计入。与偷渡客和未转化的猫不同，这一排除带条件，因此不能使用 SparkFactionAPI 按职业 id 静态注册的
 * {@code SettlementExclusions}。
 *
 * <p>Seams: every {@code CheckWinCondition} listener through {@code StowawayWinScope}
 * ({@code mixin/ferryman/GameFunctionsFerrymanSettlementMixin}), and the NoellesRoles moment-trigger and head counts,
 * the Taotie and {@link FerrymanMomentRules#outsidersAlive} through {@code FiendWinExclusion.countsAsAlive}. Counts
 * that SparkTraits and SparkStrength make on their own outside Wathe's win dispatch do not see it. Server only: the
 * moment component is never synced, so a client would read every Charon as excluded.
 * 接缝：所有 {@code CheckWinCondition} 监听器经由 {@code StowawayWinScope}
 * （{@code mixin/ferryman/GameFunctionsFerrymanSettlementMixin}），NoellesRoles 的时刻触发与人数计数、饕餮以及
 * {@link FerrymanMomentRules#outsidersAlive} 经由 {@code FiendWinExclusion.countsAsAlive}。SparkTraits 与 SparkStrength
 * 在 Wathe 胜负分发之外自行进行的计数看不到这条规则。仅服务端：时刻组件从不同步，客户端会把每个卡戎都读成被排除。</p>
 *
 * <p>The answer is deliberately identical to the one {@link FerrymanMomentRules#outsidersAlive} already gives: an empty
 * or inactive moment contributes nothing, and only a running moment with souls makes its own Charon matter.
 * 该判定刻意与 {@link FerrymanMomentRules#outsidersAlive} 已有的回答保持一致：空时刻或不存在的时刻不产生任何影响，只有
 * 正在进行且拥有灵魂的时刻才让它自己的卡戎变得重要。</p>
 */
public final class FerrymanSettlement {
    private FerrymanSettlement() {
    }

    /**
     * Pure rule: a Charon counts as a living participant only while the moment he owns is running with at least one
     * soul. Another Charon's moment never lifts it.
     * 纯规则：只有卡戎自己拥有的时刻正在进行且至少有一名灵魂时，他才算存活参与者。别的卡戎的时刻不会解除排除。
     */
    public static boolean isExcluded(boolean ferryman, boolean ownsRunningMoment, int soulCount) {
        return ferryman && !(ownsRunningMoment && soulCount > 0);
    }

    /** Whether this player's alive answer must be dropped from win counts. / 该玩家的存活判断是否必须从胜负计数中剔除。 */
    public static boolean isExcluded(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        World world = player.getWorld();
        if (world == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
        if (game == null || !FerrymanRules.isFerryman(game.getRole(player))) {
            return false;
        }
        FerrymanMomentWorldComponent moment = FerrymanMomentWorldComponent.get(world);
        return isExcluded(true, moment.owns(player.getUuid()), moment.soulCount());
    }

    /**
     * Keeps the host's alive answer, except for a Charon with no souls. Dead players skip the role lookup.
     * 保留宿主的存活判断结果，唯独没有灵魂的卡戎不计入。已死亡玩家不会进行职业判断。
     */
    public static boolean countsAsAlive(boolean hostAlive, @Nullable PlayerEntity player) {
        return hostAlive && !isExcluded(player);
    }
}
