package dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * H2 "one roll per shot": the outcome of each {@link BearRicochetRules.ShotKey} for the current server tick only. Any
 * other tick clears it first, so it never grows past one tick's shots (capped at {@link #MAX_ENTRIES} as a guard).
 * Server thread only.
 * H2“每枪只掷一次骰”：只保存当前服务端刻内每个 {@link BearRicochetRules.ShotKey} 的结果。遇到其他刻先清空，因此不会超过
 * 一刻内的射击数（另以 {@link #MAX_ENTRIES} 作为上限保护）。仅限服务端线程。
 */
final class RicochetMemo {
    static final int MAX_ENTRIES = 512;

    private final Map<BearRicochetRules.ShotKey, Boolean> outcomes = new HashMap<>();
    private long tick = Long.MIN_VALUE;

    /** The earlier answer for this shot, or null when it has not rolled. / 这一枪先前的结果；尚未掷骰时为 null。 */
    @Nullable Boolean get(BearRicochetRules.ShotKey key) {
        advance(key.tick());
        return outcomes.get(key);
    }

    void put(BearRicochetRules.ShotKey key, boolean ricochet) {
        advance(key.tick());
        if (outcomes.size() < MAX_ENTRIES || outcomes.containsKey(key)) {
            outcomes.put(key, ricochet);
        }
    }

    int size() {
        return outcomes.size();
    }

    private void advance(long now) {
        if (now != tick) {
            outcomes.clear();
            tick = now;
        }
    }
}
