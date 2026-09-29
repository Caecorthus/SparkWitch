package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * C15: a dormant Fiend cannot die, so it is never a lasting kill target: NoellesRoles' Serial Killer never selects or
 * keeps one (the Bodyguard copies that target, so it follows). Reached only through
 * {@code mixin/fiend/SerialKillerPlayerComponentFiendTargetMixin}. A moment Fiend, a spent Fiend and every other
 * player keep the host's own answer. Server only.
 * C15：休眠魔人无法死亡，因此永远不是持续的击杀目标：NoellesRoles 连环杀手绝不选中或保留它（保镖复制该目标，随之生效）。
 * 仅经由 {@code mixin/fiend/SerialKillerPlayerComponentFiendTargetMixin} 接入。时刻中的魔人、已耗尽的魔人与其他所有玩家
 * 保持宿主原本的结果。仅服务端。
 */
public final class FiendTargetExclusion {
    private FiendTargetExclusion() {
    }

    /** The host's validity, except that a dormant Fiend is never valid. / 保留宿主的有效性判断，唯独休眠魔人永不有效。 */
    public static boolean isValidTarget(boolean hostValid, ServerWorld world, @Nullable UUID target) {
        return hostValid && !isDormantFiend(world, target);
    }

    /** The host's candidates minus every dormant Fiend. / 宿主候选列表去除所有休眠魔人。 */
    public static List<UUID> eligibleTargets(List<UUID> hostCandidates, ServerWorld world) {
        return without(hostCandidates, target -> isDormantFiend(world, target));
    }

    /**
     * Pure filter: the host list itself when nothing is excluded, otherwise a new list in the same order.
     * 纯过滤：无需排除时返回宿主列表本身，否则按原顺序返回新列表。
     */
    static <T> List<T> without(List<T> candidates, Predicate<T> excluded) {
        if (candidates.stream().noneMatch(excluded)) {
            return candidates;
        }
        List<T> kept = new ArrayList<>(candidates.size());
        for (T candidate : candidates) {
            if (!excluded.test(candidate)) {
                kept.add(candidate);
            }
        }
        return kept;
    }

    private static boolean isDormantFiend(ServerWorld world, @Nullable UUID target) {
        PlayerEntity player = target == null ? null : world.getPlayerByUuid(target);
        return FiendParticipation.isDormantFiend(player);
    }
}
