package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.PoliceRoles;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter.BlackRavenDisguiseAdapters;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.RoleSelectionContext;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.ScoreboardRoleSelectorComponent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Tab B snapshot taken once per Raven at ON_FINISH_INITIALIZE: civilian-base, enabled, able-to-appear
 * roles absent from the raw role map, flagged by {@link BlackRavenDisguiseRules#classify}. Never records
 * history and never changes during the round.
 * 每名黑羽鸦在开局完成时拍摄一次的 Tab B 快照：平民基础阵营、已启用、本局可出现且未出现在真实职业表中的职业，
 * 按 classify 标记。从不记录历史，本局内不再变化。
 */
public final class BlackRavenDisguisePool {
    private BlackRavenDisguisePool() {
    }

    /**
     * One evaluated role before listing. Every input is read from raw state (role map, faction, appearance).
     * 列出前已求值的单个职业；所有输入均读取真实状态（职业表、阵营、出现条件）。
     */
    public record Candidate(
            Identifier id,
            boolean policeByApi,
            boolean civilianBase,
            boolean enabled,
            boolean canAppear,
            boolean absent
    ) {
    }

    /** Rows in WatheRoles.ROLES order, at most MAX_POOL_ENTRIES. / 按 WatheRoles.ROLES 顺序的行，最多 MAX_POOL_ENTRIES。 */
    public static List<BlackRavenDisguiseRules.PoolEntry> snapshot(ServerWorld world, GameWorldComponent game) {
        RoleSelectionContext context = selectionContext(world, game);
        List<Candidate> candidates = new ArrayList<>();
        for (Role role : List.copyOf(WatheRoles.ROLES)) {
            Candidate candidate = evaluate(role, game, context);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        return build(candidates, BlackRavenDisguiseAdapters::isShipped);
    }

    /**
     * Pure pool builder: keeps listed candidates in input order, skips duplicate ids, classifies each row, and caps
     * the result at MAX_POOL_ENTRIES.
     * 纯快照构建：按输入顺序保留可列出的候选、跳过重复 id、逐行分类，并将结果截断到 MAX_POOL_ENTRIES。
     */
    public static List<BlackRavenDisguiseRules.PoolEntry> build(List<Candidate> candidates, Predicate<Identifier> shipped) {
        List<BlackRavenDisguiseRules.PoolEntry> rows = new ArrayList<>();
        Set<Identifier> seen = new HashSet<>();
        for (Candidate candidate : candidates) {
            if (rows.size() >= BlackRavenDisguiseRules.MAX_POOL_ENTRIES) {
                break;
            }
            if (candidate == null || candidate.id() == null || !seen.add(candidate.id())) {
                continue;
            }
            if (!isListed(candidate.civilianBase(), candidate.enabled(), candidate.canAppear(), candidate.absent())) {
                continue;
            }
            Identifier id = candidate.id();
            rows.add(new BlackRavenDisguiseRules.PoolEntry(
                    id,
                    BlackRavenDisguiseRules.classify(id, candidate.policeByApi(), shipped.test(id))
            ));
        }
        return List.copyOf(rows);
    }

    /** Pure listing gate. / 纯列出条件。 */
    public static boolean isListed(boolean civilianBase, boolean enabled, boolean canAppear, boolean absent) {
        return civilianBase && enabled && canAppear && absent;
    }

    private static @Nullable Candidate evaluate(
            @Nullable Role role,
            GameWorldComponent game,
            @Nullable RoleSelectionContext context
    ) {
        if (role == null || role == WatheRoles.NO_ROLE || role.identifier() == null) {
            return null;
        }
        // Each probe fails closed: an exception unlists the role instead of aborting the snapshot.
        // 每项探测均失败即关闭：异常只会让该职业不被列出，而不会中断整个快照。
        boolean civilianBase = safely(() -> FactionIds.CIVILIAN.equals(SparkFactionApi.resolveBaseFaction(role)));
        boolean enabled = civilianBase && safely(() -> game.isRoleEnabled(role));
        boolean canAppear = enabled && context != null && safely(() -> role.shouldAppear(context));
        boolean absent = canAppear && safely(() -> game.getAllWithRole(role).isEmpty());
        boolean police = safely(() -> PoliceRoles.contains(role));
        return new Candidate(role.identifier(), police, civilianBase, enabled, canAppear, absent);
    }

    private static @Nullable RoleSelectionContext selectionContext(ServerWorld world, GameWorldComponent game) {
        try {
            List<ServerPlayerEntity> participants = new ArrayList<>();
            for (UUID uuid : game.getAllPlayers()) {
                ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
                if (player != null) {
                    participants.add(player);
                }
            }
            return ScoreboardRoleSelectorComponent.KEY.get(world.getScoreboard())
                    .createSelectionContext(world, game, participants);
        } catch (RuntimeException ignored) {
            // No context means no role can prove it may appear: the pool is empty (fail closed).
            // 无法得到上下文即无法证明任何职业可出现：快照为空（失败即关闭）。
            return null;
        }
    }

    private static boolean safely(BooleanProbe probe) {
        try {
            return probe.test();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @FunctionalInterface
    private interface BooleanProbe {
        boolean test();
    }
}
