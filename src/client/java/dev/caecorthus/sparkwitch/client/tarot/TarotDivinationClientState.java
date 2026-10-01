package dev.caecorthus.sparkwitch.client.tarot;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.client.MinecraftClient;

public final class TarotDivinationClientState {
    private static final TarotDivinationSnapshotState SNAPSHOT = new TarotDivinationSnapshotState();

    private TarotDivinationClientState() {
    }

    public static TarotDivinationSnapshotState snapshotState() {
        return SNAPSHOT;
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null || !SparkWitchServerConnection.isConfirmedServer()) {
            SNAPSHOT.clear();
            return;
        }

        GameWorldComponent game = GameWorldComponent.KEY.get(client.player.getWorld());
        // Widened by the Black Raven acting overlay; getRole stays raw. / 黑羽鸦扮演覆盖层会放宽此判定；getRole 仍为真实身份。
        boolean exactTarotReader = game.isRole(client.player, SparkWitchRoles.tarotReader());
        SNAPSHOT.retainFor(true, game.isRunning(), exactTarotReader);
    }

    public static void clear() {
        SNAPSHOT.clear();
    }
}
