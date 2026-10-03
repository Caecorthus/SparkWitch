package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One-shot Prophecy sessions keyed by the Prophet. A session carries a random nonce, the match id it was opened in,
 * the candidate victims shown, and an expiry tick; confirming consumes it exactly once, so a stale or replayed
 * confirmation can neither consume a replacement session nor cross into another round. Opening is throttled.
 * 以先知为键的一次性预言会话。会话包含随机标识、开启时的对局 id、展示的候选死者以及过期 tick；确认时恰好消费一次，
 * 因此过期或重放的确认既不能消费替换后的会话，也不能跨越到另一局。打开操作有节流。
 */
public final class ProphetProphecySessions {
    /** Generous: the Prophet may read the necrology before choosing. / 较宽裕：先知可以先翻阅名录再做选择。 */
    public static final int SESSION_TICKS = 60 * 20;
    public static final int OPEN_INTERVAL_TICKS = 5;

    private final Map<UUID, Session> pending = new HashMap<>();
    private final Map<UUID, Long> lastOpened = new HashMap<>();

    public synchronized Optional<Session> open(UUID actor, UUID match, Collection<UUID> victims, long now) {
        if (actor == null || match == null || victims == null || victims.isEmpty()) {
            return Optional.empty();
        }
        expire(now);
        Long previous = lastOpened.get(actor);
        if (previous != null && now - previous < OPEN_INTERVAL_TICKS) {
            return Optional.empty();
        }
        Session session = new Session(UUID.randomUUID(), match, Set.copyOf(victims), now + SESSION_TICKS);
        pending.put(actor, session);
        lastOpened.put(actor, now);
        return Optional.of(session);
    }

    /**
     * Removes the actor's session when the nonce matches, then accepts it only for the same match before expiry.
     * A wrong nonce leaves the current session untouched.
     * 标识匹配时移除该会话，并且只在同一对局且未过期时接受。标识错误时保留当前会话。
     */
    public synchronized Optional<Session> consume(UUID actor, UUID nonce, UUID match, long now) {
        Session session = actor == null ? null : pending.get(actor);
        if (session == null || nonce == null || !session.nonce().equals(nonce)) {
            return Optional.empty();
        }
        pending.remove(actor);
        return session.match().equals(match) && now < session.expiresAt()
                ? Optional.of(session) : Optional.empty();
    }

    public synchronized void invalidate(UUID actor) {
        pending.remove(actor);
        lastOpened.remove(actor);
    }

    public synchronized void expire(long now) {
        pending.entrySet().removeIf(entry -> now >= entry.getValue().expiresAt());
        lastOpened.entrySet().removeIf(entry -> now - entry.getValue() >= OPEN_INTERVAL_TICKS);
    }

    public synchronized boolean isEmpty() {
        return pending.isEmpty() && lastOpened.isEmpty();
    }

    public synchronized void clear() {
        pending.clear();
        lastOpened.clear();
    }

    public record Session(UUID nonce, UUID match, Set<UUID> victims, long expiresAt) {
        public Session {
            victims = Set.copyOf(victims);
        }
    }
}
