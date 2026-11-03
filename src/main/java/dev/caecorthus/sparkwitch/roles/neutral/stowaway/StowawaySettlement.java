package dev.caecorthus.sparkwitch.roles.neutral.stowaway;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Settlement exclusion: a Stowaway NEVER counts as a living player for win determination, so every win, block and
 * moment comes out as if the Stowaway were not in the round. Seams: every {@code CheckWinCondition} listener through
 * {@link StowawayWinScope}, the NoellesRoles moment-trigger and head counts through
 * {@code FiendWinExclusion.countsAsAlive}, and SparkTraits / SparkStrength through SparkFactionAPI's
 * {@code SettlementExclusions}. Its real death state is untouched: at settlement a living Stowaway joins the winning
 * side ({@link #completeRoundEndRows}) while a dead one loses.
 * 结算排除：偷渡客在胜负判定中永远不算存活，因此所有胜利、阻止与时刻的结果都与偷渡客不在本局时完全相同。接缝：所有
 * {@code CheckWinCondition} 监听器经由 {@link StowawayWinScope}，NoellesRoles 的时刻触发与人数计数经由
 * {@code FiendWinExclusion.countsAsAlive}，SparkTraits / SparkStrength 经由 SparkFactionAPI 的
 * {@code SettlementExclusions}。其真实死亡状态不受影响：结算时活着的偷渡客加入获胜一方
 * （{@link #completeRoundEndRows}），死亡的偷渡客判负。
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
     * Round-end rule (owner 2026-11-02): once the round has a winner — a team result (KILLERS / PASSENGERS / TIME) or
     * any winning row that is not a Stowaway — every ALIVE Stowaway row becomes a winner; DEAD, LEFT and LEFT_DEAD
     * rows keep their flag. Every winning Stowaway row is then moved behind all other rows (relative order kept), because
     * Wathe titles a neutral win with the first winning row and the rows come in role-map order. Idempotent.
     * 回合结束规则（所有者 2026-11-02）：本局已有胜者时——阵营结果（KILLERS / PASSENGERS / TIME）或任一非偷渡客的获胜行——
     * 每条 ALIVE 的偷渡客行都转为赢家；DEAD、LEFT 与 LEFT_DEAD 行保持原标记。随后把所有获胜的偷渡客行移到其他行之后
     * （保持相对顺序），因为 Wathe 用第一条获胜行作为中立胜利标题，而各行按职业表顺序生成。可重复执行。
     */
    public static void completeRoundEndRows(
            List<GameRoundEndComponent.RoundEndData> rows,
            GameFunctions.WinStatus status
    ) {
        if (status == GameFunctions.WinStatus.NONE || rows.isEmpty()) {
            return;
        }
        boolean teamWin = status == GameFunctions.WinStatus.KILLERS
                || status == GameFunctions.WinStatus.PASSENGERS
                || status == GameFunctions.WinStatus.TIME;
        boolean otherWinner = false;
        for (GameRoundEndComponent.RoundEndData row : rows) {
            if (row.isWinner() && !isStowawayRow(row)) {
                otherWinner = true;
                break;
            }
        }
        if (!teamWin && !otherWinner) {
            return;
        }
        List<GameRoundEndComponent.RoundEndData> winningStowaways = new ArrayList<>();
        Iterator<GameRoundEndComponent.RoundEndData> iterator = rows.iterator();
        while (iterator.hasNext()) {
            GameRoundEndComponent.RoundEndData row = iterator.next();
            if (!isStowawayRow(row)) {
                continue;
            }
            if (row.isWinner()) {
                winningStowaways.add(row);
                iterator.remove();
            } else if (row.endStatus() == GameRoundEndComponent.PlayerEndStatus.ALIVE) {
                winningStowaways.add(new GameRoundEndComponent.RoundEndData(
                        row.player(), row.role(), row.endStatus(), true));
                iterator.remove();
            }
        }
        rows.addAll(winningStowaways);
    }

    /** Rows carry the raw role identifier. / 结算行记录的是原始职业标识。 */
    private static boolean isStowawayRow(GameRoundEndComponent.RoundEndData row) {
        return StowawayRules.ROLE_ID.equals(row.role());
    }
}
