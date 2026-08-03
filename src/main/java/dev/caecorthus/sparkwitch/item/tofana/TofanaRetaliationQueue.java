package dev.caecorthus.sparkwitch.item.tofana;

import java.util.ArrayDeque;
import java.util.function.Consumer;

public final class TofanaRetaliationQueue<T> {
    private final ArrayDeque<Attempt<T>> ready = new ArrayDeque<>();
    private final ArrayDeque<Attempt<T>> pending = new ArrayDeque<>();

    public void enqueue(T holder, T attacker) {
        pending.addLast(new Attempt<>(holder, attacker));
    }

    public void tick(Consumer<Attempt<T>> consumer) {
        Attempt<T> attempt;
        while ((attempt = ready.pollFirst()) != null) {
            consumer.accept(attempt);
        }
        ready.addAll(pending);
        pending.clear();
    }

    public int size() {
        return ready.size() + pending.size();
    }

    public void clear() {
        ready.clear();
        pending.clear();
    }

    public record Attempt<T>(T holder, T attacker) {
    }
}
