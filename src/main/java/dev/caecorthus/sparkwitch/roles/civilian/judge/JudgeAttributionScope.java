package dev.caecorthus.sparkwitch.roles.civilian.judge;

import java.util.UUID;

/** Identity-scoped, reentrant attribution; no entity or online lookup. / 按世界对象隔离、可重入的 UUID 归属。 */
final class JudgeAttributionScope<W> {
    private final ThreadLocal<Entry<W>> current = new ThreadLocal<>();

    UUID actor(W world) {
        Entry<W> entry = current.get();
        return entry != null && entry.world == world ? entry.actor : null;
    }

    void runWith(W world, UUID actor, Runnable action) {
        Entry<W> previous = current.get();
        current.set(new Entry<>(world, actor));
        try {
            action.run();
        } finally {
            if (previous == null) current.remove();
            else current.set(previous);
        }
    }

    private record Entry<W>(W world, UUID actor) {}
}
