package dev.caecorthus.sparkwitch.roles.civilian.controlexpert;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Server-thread registry of effect instances the stun created. Vanilla merges a new effect into an existing instance
 * of the same type, so ownership is claimed only for a type that was absent before the stun, and removal compares
 * the live map object by identity. A pre-existing (foreign) effect is therefore never removed.
 * 眩晕所创建效果实例的服务端线程登记表。原版会把新效果合并进同类型的已有实例，因此只在眩晕前该类型不存在时
 * 才登记归属，移除时按身份比较实时对象；眩晕前已存在的（他人的）效果因此永远不会被移除。
 */
final class ControlExpertOwnedEffects<K, V> {
    private final Map<UUID, Map<K, V>> owned = new HashMap<>();

    /**
     * Claims {@code live} only when the type was absent before; otherwise forgets a record that no longer matches.
     * 仅在该类型此前不存在时登记 {@code live}；否则遗忘已不匹配的旧记录。
     */
    void record(UUID target, K type, boolean absentBefore, @Nullable V live) {
        if (absentBefore && live != null) {
            owned.computeIfAbsent(target, ignored -> new HashMap<>()).put(type, live);
            return;
        }
        Map<K, V> records = owned.get(target);
        if (records != null && records.get(type) != live) {
            records.remove(type);
            if (records.isEmpty()) {
                owned.remove(target);
            }
        }
    }

    /**
     * Forgets the target and returns the types whose live instance is still exactly the recorded one.
     * 遗忘该目标，并返回实时实例仍为所登记实例的效果类型。
     */
    List<K> release(UUID target, Function<K, V> current) {
        Map<K, V> records = owned.remove(target);
        List<K> removable = new ArrayList<>();
        if (records != null) {
            records.forEach((type, instance) -> {
                if (current.apply(type) == instance) {
                    removable.add(type);
                }
            });
        }
        return removable;
    }

    void clear() {
        owned.clear();
    }
}
