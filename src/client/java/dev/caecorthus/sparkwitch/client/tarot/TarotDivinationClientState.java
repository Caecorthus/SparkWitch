package dev.caecorthus.sparkwitch.client.tarot;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.client.MinecraftClient;

public final class TarotDivinationClientState {
    private static final TarotDivinationSnapshotState SNAPSHOT = new TarotDivinationSnapshotState();
    private static final TarotReadingLog READINGS = new TarotReadingLog();

    private TarotDivinationClientState() {
    }

    public static TarotDivinationSnapshotState snapshotState() {
        return SNAPSHOT;
    }

    public static TarotReadingLog readingLog() {
        return READINGS;
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null || !SparkWitchServerConnection.isConfirmedServer()) {
            clear();
            return;
        }

        GameWorldComponent game = GameWorldComponent.KEY.get(client.player.getWorld());
        Role role = game.getRole(client.player);
        boolean exactTarotReader = role != null
                && SparkWitchRoles.TAROT_READER_ID.equals(role.identifier());
        boolean runningRound = game.isRunning();
        SNAPSHOT.retainFor(true, runningRound, exactTarotReader);
        if (!runningRound || !exactTarotReader) {
            // The readings are round-scoped and leave on exactly the edge that clears the snapshot.
            // 占卜记录以本局为限，与快照在完全相同的条件下清空。
            READINGS.clear();
        }
    }

    public static void clear() {
        SNAPSHOT.clear();
        READINGS.clear();
    }
}
