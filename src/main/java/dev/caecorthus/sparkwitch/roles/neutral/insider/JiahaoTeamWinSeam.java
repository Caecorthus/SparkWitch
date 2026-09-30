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
 * {@code getAllWithRole} walks. In a round without an Insider every entry point returns the host value (C13). Server
 * only.
 * 嘉豪阵营 mixin 与 {@link JiahaoTeamWinRules} 之间的服务端适配器：读取 Wathe 职业表与存活状态以及 NoellesRoles 的
 * 吞噬标记（硬依赖，固定 {@code b58fa5f}），再把规则给出的 UUID 映射回玩家。快照沿用 Wathe 职业表的顺序，也就是
 * NoellesRoles 自身 {@code getAllWithRole} 的遍历顺序。本局没有内应时，每个入口都返回宿主原值（C13）。仅服务端。
 */
public final class JiahaoTeamWinSeam {
    private JiahaoTeamWinSeam() {
    }

    /**
     * Team list that replaces the Corrupt Cop lookup list; {@code hostLookup} when the round has no Insider.
     * 替换黑警查找列表的阵营成员列表；本局没有内应时为 {@code hostLookup}。
     */
    public static List<UUID> memberLookup(ServerWorld world, GameWorldComponent game, List<UUID> hostLookup) {
        return JiahaoTeamWinRules.lookup(members(world, game), hostLookup);
    }

    /** One per-player answer of the Corrupt Cop {@code aliveCount} loop. / 黑警 {@code aliveCount} 循环中的逐人结果。 */
    public static boolean countsTowardAliveCount(boolean hostAlive, PlayerEntity player) {
        if (!hostAlive || !(player.getWorld() instanceof ServerWorld world) || !InsiderParticipation.isTeamMember(player)) {
            return hostAlive;
        }
        return JiahaoTeamWinRules.countsTowardAliveCount(true, player.getUuid(),
                members(world, GameWorldComponent.KEY.get(world)));
    }

    /**
     * The team's win in place of NoellesRoles' single-winner result; {@code hostResult} is kept when the winners are
     * exactly NoellesRoles' own (a round without an Insider, or a lone online member). Offline members cannot be named
     * in a {@code WinResult}; {@link #creditTeamJiahaoRows} credits their round-end rows.
     * 用阵营胜利替换 NoellesRoles 的单人胜利结果；胜者恰为 NoellesRoles 自身胜者（本局没有内应，或只有一名在线成员）时保留
     * {@code hostResult}。离线成员无法列入 {@code WinResult}，由 {@link #creditTeamJiahaoRows} 在结算行中计为获胜。
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

    /**
     * Rewrites Wathe's explicit-winner rows before they sync, see {@link JiahaoTeamWinRules#roundEndCredit}: a Team
     * Jiahao win credits every team row, offline ones included (C14), and leads with an Insider row (D7). Wathe's
     * {@code didWin} and {@code GameRecordManager.endMatch} read these same rows, so they agree with the end screen.
     * 在 Wathe 显式胜者结算行同步前改写它们，见 {@link JiahaoTeamWinRules#roundEndCredit}：嘉豪阵营胜利时所有阵营行（含离线行，
     * C14）计为获胜，并由内应行领衔（D7）。Wathe 的 {@code didWin} 与 {@code GameRecordManager.endMatch} 读取同一批结算行，
     * 因此与结算界面一致。
     */
    public static void creditTeamJiahaoRows(ServerWorld world, List<GameRoundEndComponent.RoundEndData> rows) {
        creditTeamJiahaoRows(rows, JiahaoTeamWinRules.appliesTo(members(world, GameWorldComponent.KEY.get(world))));
    }

    static void creditTeamJiahaoRows(List<GameRoundEndComponent.RoundEndData> rows, boolean roundHasInsider) {
        List<JiahaoTeamWinRules.RoundEndRow> view = new ArrayList<>(rows.size());
        for (GameRoundEndComponent.RoundEndData row : rows) {
            view.add(new JiahaoTeamWinRules.RoundEndRow(row.role(), row.isWinner()));
        }
        JiahaoTeamWinRules.RoundEndCredit credit = JiahaoTeamWinRules.roundEndCredit(view, roundHasInsider);
        for (int index : credit.newWinners()) {
            GameRoundEndComponent.RoundEndData row = rows.get(index);
            rows.set(index, new GameRoundEndComponent.RoundEndData(row.player(), row.role(), row.endStatus(), true));
        }
        if (credit.leadRow() > 0) {
            rows.add(0, rows.remove(credit.leadRow()));
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
