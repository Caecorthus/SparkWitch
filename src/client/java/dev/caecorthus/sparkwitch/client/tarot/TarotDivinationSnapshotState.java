package dev.caecorthus.sparkwitch.client.tarot;

import java.util.Optional;

/**
 * Owns the purchaser-only client snapshot; the server remains authoritative for its contents.
 * {@code previous} is the snapshot the latest purchase replaced, kept only so the HUD can mark changes between two
 * readings this client already received; it never outlives the current snapshot.
 * 保存仅购买者可见的客户端快照；快照内容仍以服务端为准。{@code previous} 为最近一次购买所替换的快照，
 * 仅用于在 HUD 上标出本客户端已收到的两次结果之间的变化，且不会比当前快照保留得更久。
 */
public final class TarotDivinationSnapshotState {
    private Snapshot snapshot;
    private Snapshot previous;

    public void overwrite(int civilianCount, int killerCount, int neutralCount, int witchCount) {
        previous = snapshot;
        snapshot = new Snapshot(civilianCount, killerCount, neutralCount, witchCount);
    }

    public Optional<Snapshot> snapshot() {
        return Optional.ofNullable(snapshot);
    }

    public Optional<Snapshot> previous() {
        return Optional.ofNullable(previous);
    }

    public boolean retainFor(boolean confirmedServer, boolean runningRound, boolean exactTarotReader) {
        if (!confirmedServer || !runningRound || !exactTarotReader) {
            clear();
            return false;
        }
        return snapshot != null;
    }

    public void clear() {
        snapshot = null;
        previous = null;
    }

    public record Snapshot(int civilianCount, int killerCount, int neutralCount, int witchCount) {
    }
}
