package dev.caecorthus.sparkwitch.client.tarot;

import java.util.Optional;

/**
 * Owns the purchaser-only client snapshot; the server remains authoritative for its contents.
 * 保存仅购买者可见的客户端快照；快照内容仍以服务端为准。
 */
public final class TarotDivinationSnapshotState {
    private Snapshot snapshot;

    public void overwrite(int civilianCount, int killerCount, int neutralCount, int witchCount) {
        snapshot = new Snapshot(civilianCount, killerCount, neutralCount, witchCount);
    }

    public Optional<Snapshot> snapshot() {
        return Optional.ofNullable(snapshot);
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
    }

    public record Snapshot(int civilianCount, int killerCount, int neutralCount, int witchCount) {
    }
}
