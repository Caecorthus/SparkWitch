package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendRules.Hit;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Pure per-attack de-duplication: one reaction per (Fiend, attacker, hit kind) per server tick, so SparkTraits Heavy
 * Artillery's second same-tick {@code killPlayer} does not pay twice. Later ticks are new attacks (D6: no limit).
 * 纯粹的单次攻击去重：每个服务端刻内同一（魔人、攻击者、攻击类型）只反应一次，使 SparkTraits 重炮在同一刻的第二次
 * {@code killPlayer} 不会重复发放。之后的刻视为新的攻击（D6：不设上限）。
 */
final class FiendHitLedger {
    private long tick = Long.MIN_VALUE;
    private final Set<Key> claimed = new HashSet<>();

    boolean claim(UUID fiend, UUID attacker, Hit hit, long now) {
        if (now != tick) {
            tick = now;
            claimed.clear();
        }
        return claimed.add(new Key(Objects.requireNonNull(fiend), Objects.requireNonNull(attacker),
                Objects.requireNonNull(hit)));
    }

    void clear() {
        tick = Long.MIN_VALUE;
        claimed.clear();
    }

    private record Key(UUID fiend, UUID attacker, Hit hit) {
    }
}
