package dev.caecorthus.sparkwitch.client.tarot;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.doctor4t.wathe.api.Role;
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
        Role role = game.getRole(client.player);
        boolean exactTarotReader = role != null
                && SparkWitchRoles.TAROT_READER_ID.equals(role.identifier());
        SNAPSHOT.retainFor(true, game.isRunning(), exactTarotReader);
    }

    public static void clear() {
        SNAPSHOT.clear();
    }
}
