package dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet;

import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * H2 "one attack source, one roll" (owner 2026-11-13): the outcome of each {@link BearRicochetRules.ShotKey}. An entry
 * outlives its tick, so a source object that hits the same wearer again later (a projectile entity) reuses its answer;
 * it is forgotten {@link #SOURCE_TTL_TICKS} after its last use, when the server tick runs backwards (a new server), or
 * as the least recently used entry beyond {@link #MAX_ENTRIES}. Server thread only.
 * H2“一个攻击来源只掷一次骰”（所有者 2026-11-13）：保存每个 {@link BearRicochetRules.ShotKey} 的结果。条目会跨刻保留，因此
 * 之后再次命中同一佩戴者的来源对象（投射物实体）沿用其结果；条目在最后一次使用后 {@link #SOURCE_TTL_TICKS} 刻、服务端刻回退时
 * （新服务器）或超出 {@link #MAX_ENTRIES} 成为最久未用者时被遗忘。仅限服务端线程。
 */
final class RicochetMemo {
    static final int MAX_ENTRIES = 512;
    /**
     * Sliding timeout: the NoellesRoles throwing axe's 120 s life, the longest of the sources that carry an entity
     * identity (the others land within one tick). / 滑动超时：NoellesRoles 飞斧的 120 秒寿命，是带实体身份的来源中最长的
     * （其余来源都在一刻内落定）。
     */
    static final long SOURCE_TTL_TICKS = 2400L;

    /** Outcome and the tick it was last used. / 结果及其最后一次使用的刻。 */
    private static final class Entry {
        private final boolean ricochet;
        private long lastTick;

        private Entry(boolean ricochet, long lastTick) {
            this.ricochet = ricochet;
            this.lastTick = lastTick;
        }
    }

    /** Access order: the head is the least recently used entry. / 访问顺序：队首是最久未用的条目。 */
    private final LinkedHashMap<BearRicochetRules.ShotKey, Entry> outcomes = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<BearRicochetRules.ShotKey, Entry> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    /**
     * The earlier answer of this source for this wearer, or null when it has not rolled.
     * 该来源对该佩戴者先前的结果；尚未掷骰时为 null。
     */
    @Nullable Boolean get(BearRicochetRules.ShotKey key, long now) {
        expire(now);
        Entry entry = outcomes.get(key);
        if (entry == null) {
            return null;
        }
        entry.lastTick = now;
        return entry.ricochet;
    }

    void put(BearRicochetRules.ShotKey key, boolean ricochet, long now) {
        expire(now);
        outcomes.put(key, new Entry(ricochet, now));
    }

    int size() {
        return outcomes.size();
    }

    /**
     * Drops expired entries from the least recently used end; every use stamps the current tick, so the first fresh
     * entry ends the walk. / 从最久未用的一端丢弃过期条目；每次使用都记下当前刻，因此遇到第一个未过期条目即可停止。
     */
    private void expire(long now) {
        Iterator<Entry> iterator = outcomes.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (now >= entry.lastTick && now - entry.lastTick <= SOURCE_TTL_TICKS) {
                return;
            }
            iterator.remove();
        }
    }
}
