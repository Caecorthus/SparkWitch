package dev.caecorthus.sparkwitch.client.tarot;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
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
        // Widened by the Black Raven acting overlay; getRole stays raw. / 黑羽鸦扮演覆盖层会放宽此判定；getRole 仍为真实身份。
        boolean exactTarotReader = game.isRole(client.player, SparkWitchRoles.tarotReader());
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
