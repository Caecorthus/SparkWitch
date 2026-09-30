package dev.caecorthus.sparkwitch.roles.neutral.insider;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.CheckWinCondition;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server adapter between the Team Jiahao mixins and {@link JiahaoTeamWinRules}: it reads Wathe's role map and
 * liveness plus NoellesRoles' swallow flag (hard dependency, pinned {@code b58fa5f}), and maps the rules' UUIDs back
 * to players. Snapshots follow Wathe's role map order, which is also the order NoellesRoles' own
 * {@code getAllWithRole} walks. Server only.
 * 嘉豪阵营 mixin 与 {@link JiahaoTeamWinRules} 之间的服务端适配器：读取 Wathe 职业表与存活状态以及 NoellesRoles 的
 * 吞噬标记（硬依赖，固定 {@code b58fa5f}），再把规则给出的 UUID 映射回玩家。快照沿用 Wathe 职业表的顺序，也就是
 * NoellesRoles 自身 {@code getAllWithRole} 的遍历顺序。仅服务端。
 */
public final class JiahaoTeamWinSeam {
    private JiahaoTeamWinSeam() {
    }

    /** Team list that replaces the Corrupt Cop lookup list. / 替换黑警查找列表的阵营成员列表。 */
    public static List<UUID> memberLookup(ServerWorld world, GameWorldComponent game) {
        return JiahaoTeamWinRules.lookupOrder(members(world, game));
    }

    /** One per-player answer of the Corrupt Cop {@code aliveCount} loop. / 黑警 {@code aliveCount} 循环中的逐人结果。 */
    public static boolean countsTowardAliveCount(boolean hostAlive, PlayerEntity player) {
        if (!hostAlive || !(player.getWorld() instanceof ServerWorld world) || !InsiderParticipation.isTeamMember(player)) {
            return hostAlive;
        }
        UUID representative = JiahaoTeamWinRules.representative(members(world, GameWorldComponent.KEY.get(world)));
        return JiahaoTeamWinRules.countsTowardAliveCount(true, true, player.getUuid().equals(representative));
    }

    /**
     * The team's win in place of NoellesRoles' single-winner result; {@code hostResult} is kept when the winners are
     * exactly NoellesRoles' own (a lone Corrupt Cop).
     * 用阵营胜利替换 NoellesRoles 的单人胜利结果；胜者恰为 NoellesRoles 自身胜者（只有一名黑警）时保留 {@code hostResult}。
     */
    public static CheckWinCondition.WinResult teamWin(
            @Nullable ServerPlayerEntity livingMember,
            CheckWinCondition.WinResult hostResult
    ) {
        if (livingMember == null) {
            return hostResult;
        }
        ServerWorld world = livingMember.getServerWorld();
        JiahaoTeamWinRules.Winners winners = JiahaoTeamWinRules.winners(
                members(world, GameWorldComponent.KEY.get(world)),
                livingMember.getUuid()
        );
        if (JiahaoTeamWinRules.isHostWin(winners, livingMember.getUuid())) {
            return hostResult;
        }
        ServerPlayerEntity primary = player(world, winners.primary());
        if (primary == null) {
            return hostResult;
        }
        List<ServerPlayerEntity> coWinners = new ArrayList<>();
        for (UUID id : winners.coWinners()) {
            ServerPlayerEntity coWinner = player(world, id);
            if (coWinner != null) {
                coWinners.add(coWinner);
            }
        }
        return CheckWinCondition.WinResult.neutralWin(primary, coWinners);
    }

    /** Moves a winning Insider row first so Wathe's end title reads Team Jiahao (D7). / 把获胜内应行移到最前，使 Wathe 结算标题显示嘉豪阵营（D7）。 */
    public static void leadWithTeamJiahaoTitle(List<GameRoundEndComponent.RoundEndData> rows) {
        List<JiahaoTeamWinRules.RoundEndRow> view = new ArrayList<>(rows.size());
        for (GameRoundEndComponent.RoundEndData row : rows) {
            view.add(new JiahaoTeamWinRules.RoundEndRow(row.role(), row.isWinner()));
        }
        int lead = JiahaoTeamWinRules.titleLeadRow(view);
        if (lead > 0) {
            rows.add(0, rows.remove(lead));
        }
    }

    private static List<JiahaoTeamWinRules.Member> members(ServerWorld world, GameWorldComponent game) {
        List<JiahaoTeamWinRules.Member> members = new ArrayList<>();
        for (Map.Entry<UUID, Role> entry : game.getRoles().entrySet()) {
            Role role = entry.getValue();
            if (!InsiderParticipation.isTeamRole(role)) {
                continue;
            }
            PlayerEntity player = world.getPlayerByUuid(entry.getKey());
            boolean alive = GameFunctions.isPlayerPlayingAndAlive(player);
            members.add(new JiahaoTeamWinRules.Member(
                    entry.getKey(),
                    InsiderParticipation.isInsiderRole(role),
                    player instanceof ServerPlayerEntity,
                    alive,
                    alive && SwallowedPlayerComponent.isPlayerSwallowed(player)
            ));
        }
        return members;
    }

    private static @Nullable ServerPlayerEntity player(ServerWorld world, UUID id) {
        return world.getPlayerByUuid(id) instanceof ServerPlayerEntity player ? player : null;
    }
}
