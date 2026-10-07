package dev.caecorthus.sparkwitch.roles.witch.accomplice.variant;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Pure special-accomplice pool roll for one Bewitched promotion: a uniform choice among the
 * candidates that are enabled and not used this round, in candidate order; the plain Accomplice once none is left.
 * The caller supplies the world-seeded {@link Random}; it is not touched when the pool is empty.
 * 单次魔化使晋升的纯特殊共犯池抽取：按候选顺序筛出已启用且本局未使用的职业并均匀抽取；池空时返回普通共犯。
 * 调用方提供以世界随机数播种的 {@link Random}；池为空时不会消耗它。
 */
public final class AccompliceVariantRoll {
    private AccompliceVariantRoll() {
    }

    public static Role pick(
            List<Role> candidates,
            Predicate<? super Role> enabled,
            Predicate<? super Role> usedThisRound,
            Random random
    ) {
        Objects.requireNonNull(random, "random");
        List<Role> pool = new ArrayList<>(candidates.size());
        for (Role candidate : candidates) {
            if (candidate != null && enabled.test(candidate) && !usedThisRound.test(candidate)) {
                pool.add(candidate);
            }
        }
        return pool.isEmpty() ? SparkWitchRoles.accomplice() : pool.get(random.nextInt(pool.size()));
    }
}
