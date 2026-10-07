package dev.caecorthus.sparkwitch.roles.civilian.judge;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** A stale confirmation cannot consume a replacement session or authorize another round.
 * 旧确认不能消费替换后的会话，也不能跨回合取得判决权限。 */
public final class JudgeSelectionSessions {
    private static final int OPEN_INTERVAL_TICKS = 5;
    private final Map<UUID, Session> pending = new HashMap<>();
    private final Map<UUID, Long> lastOpened = new HashMap<>();

    public Optional<Session> open(UUID actor, UUID match, Collection<UUID> targets, long now) {
        if (actor == null || match == null || targets.isEmpty()
                || targets.size() > JudgeRules.MAX_SELECTION_TARGETS) {
            return Optional.empty();
        }
        Long previous = lastOpened.get(actor);
        if (previous != null && now - previous < OPEN_INTERVAL_TICKS) {
            return Optional.empty();
        }
        Session session = new Session(UUID.randomUUID(), match, Set.copyOf(targets), now + JudgeRules.SELECTION_TICKS);
        pending.put(actor, session);
        lastOpened.put(actor, now);
        return Optional.of(session);
    }

    public Optional<Session> consume(UUID actor, UUID nonce, UUID match, long now) {
        Session session = pending.get(actor);
        if (session == null || !session.nonce().equals(nonce)) {
            return Optional.empty();
        }
        pending.remove(actor);
        return session.match().equals(match) && now < session.expiresAt()
                ? Optional.of(session) : Optional.empty();
    }

    public void invalidate(UUID actor) {
        pending.remove(actor);
        lastOpened.remove(actor);
    }

    public void expire(long now) {
        pending.entrySet().removeIf(entry -> now >= entry.getValue().expiresAt());
        lastOpened.entrySet().removeIf(entry -> now - entry.getValue() >= JudgeRules.SELECTION_TICKS);
    }

    public Set<UUID> actors() {
        return Set.copyOf(pending.keySet());
    }

    public void clear() {
        pending.clear();
        lastOpened.clear();
    }

    public record Session(UUID nonce, UUID match, Set<UUID> targets, long expiresAt) {
        public Session {
            targets = Set.copyOf(targets);
        }
    }
}
