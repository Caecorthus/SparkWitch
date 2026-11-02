package dev.caecorthus.sparkwitch.roles.neutral.stowaway;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Settlement exclusion: a Stowaway NEVER counts as a living player for win determination, so every win, block and
 * moment comes out as if the Stowaway were not in the round. Its real death state is untouched: at settlement a living
 * Stowaway joins the winning team ({@link #shouldBeWinner}) while a dead one loses.
 * 结算排除：偷渡客在胜负判定中永远不算存活，因此所有胜利、阻止与时刻的结果都与偷渡客不在本局时完全相同。其真实死亡状态
 * 不受影响：结算时活着的偷渡客加入获胜阵营（{@link #shouldBeWinner}），死亡的偷渡客判负。
 */
public final class StowawaySettlement {
    private StowawaySettlement() {
    }

    /** Pure: only the raw Stowaway role is excluded. / 纯规则：只排除原始职业为偷渡客者。 */
    public static boolean isExcludedRole(@Nullable Role rawRole) {
        return StowawayRules.isStowaway(rawRole);
    }

    /**
     * Whether this player's raw role is excluded from settlement; false for a null player or a world without Wathe's
     * game component. / 该玩家的原始职业是否被排除出结算；玩家为 null 或世界没有 Wathe 对局组件时为 false。
     */
    public static boolean isExcluded(@Nullable PlayerEntity player) {
        if (player == null || player.getWorld() == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
        return game != null && isExcludedRole(game.getRole(player));
    }

    /**
     * Keeps the host's alive answer, except that the Stowaway never counts. Dead players skip the role lookup.
     * 保留宿主的存活判断结果，唯独偷渡客永不计入。已死亡玩家不会进行职业判断。
     */
    public static boolean countsAsAlive(boolean hostAlive, @Nullable PlayerEntity player) {
        return hostAlive && !isExcluded(player);
    }

    /**
     * Pure round-end rule: a not-yet-winning Stowaway row becomes a winner only while its end status is ALIVE.
     * 纯回合结束规则：尚未获胜的偷渡客行只有在结算状态为 ALIVE 时才转为赢家。
     */
    public static boolean shouldBeWinner(
            boolean alreadyWinner,
            @Nullable Role role,
            GameRoundEndComponent.PlayerEndStatus endStatus
    ) {
        return !alreadyWinner
                && endStatus == GameRoundEndComponent.PlayerEndStatus.ALIVE
                && StowawayRules.isStowaway(role);
    }
}
