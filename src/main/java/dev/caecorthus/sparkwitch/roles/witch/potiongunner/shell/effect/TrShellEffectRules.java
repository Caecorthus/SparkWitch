package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.effect;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Pure TR decisions: kill order, killer attribution, and the survivor fallback.
 * TR 的纯判定：击杀顺序、凶手归属与幸存者后备效果。
 */
final class TrShellEffectRules {
    private TrShellEffectRules() {
    }

    /**
     * Everyone else in blast order, the gunner last, so the gunner's own death cannot disturb the attribution of
     * the other kills.
     * 其他人按爆炸顺序在前，药炮手本人最后，避免其自身死亡干扰其他击杀的归属。
     */
    static <T> List<T> killOrder(List<T> hits, Predicate<T> self) {
        List<T> order = new ArrayList<>(hits.size());
        List<T> gunner = new ArrayList<>(1);
        for (T hit : hits) {
            (self.test(hit) ? gunner : order).add(hit);
        }
        order.addAll(gunner);
        return order;
    }

    /**
     * The gunner kills everyone else, allies included (Q4); the gunner's own death has no killer, so the replay
     * reads "died" and no self-kill reward path runs.
     * 药炮手是其他所有人（含队友，Q4）的凶手；药炮手自身的死亡没有凶手，回放显示「死亡」，也不会触发自杀奖励路径。
     */
    static <P> @Nullable P killer(@Nullable P gunner, boolean self) {
        return self ? null : gunner;
    }

    /**
     * A target that was alive before the attempt and still is afterwards had the kill prevented.
     * 尝试前存活、尝试后仍存活的目标即为击杀被挡下。
     */
    static boolean needsFallback(boolean livingBefore, boolean livingAfter) {
        return livingBefore && livingAfter;
    }
}
