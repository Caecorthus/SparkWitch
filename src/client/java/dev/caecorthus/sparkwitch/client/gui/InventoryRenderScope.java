package dev.caecorthus.sparkwitch.client.gui;

import java.util.ArrayDeque;
import java.util.Deque;

/** A call stack, not a cross-frame latch. Finally-closing an inner render restores its parent. */
public final class InventoryRenderScope<T> {
    public record Binding(Object screen, Object player, Object world, Object connection) {
        public boolean matches(Object screen, Object player, Object world, Object connection) {
            return this.screen == screen && this.player == player && this.world == world && this.connection == connection;
        }
    }
    private final ThreadLocal<Deque<Call<T>>> calls = ThreadLocal.withInitial(ArrayDeque::new);
    private long generation;

    public final class Scope implements AutoCloseable {
        private final Call<T> call;
        private boolean closed;
        private Scope(Call<T> call) { this.call = call; }
        public void publish(T snapshot) {
            if (closed || call.snapshot != null) throw new IllegalStateException("Inventory snapshot already committed or closed");
            call.snapshot = java.util.Objects.requireNonNull(snapshot);
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            var stack = calls.get();
            if (stack.peek() != call) throw new IllegalStateException("Inventory render scope closed out of order");
            stack.pop();
            if (stack.isEmpty()) calls.remove();
        }
    }
    private static final class Call<T> {
        final long generation;
        T snapshot;
        Call(long generation) { this.generation = generation; }
    }
    public Scope open() {
        Call<T> call = new Call<>(generation);
        calls.get().push(call); // hides parent immediately, including while preparation can fail
        return new Scope(call);
    }
    public T current() {
        Call<T> call = calls.get().peek();
        return call != null && call.generation == generation ? call.snapshot : null;
    }
    public void invalidate() { generation++; }
}
