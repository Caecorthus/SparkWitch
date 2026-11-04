package dev.caecorthus.sparkwitch.client.dominator;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorForcedShotS2CPacket;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Owner-only presentation for the Dominator's mark: an always-on dark green outline that does not need the instinct
 * key, backed by the synced {@link DominatorPlayerComponent}. The outline is answered per viewer, so no other client
 * ever learns who is marked.
 * 支配者标记的仅本人可见展示：无需本能键的常亮深绿色描边，数据来自同步的 {@link DominatorPlayerComponent}。
 * 描边按观察者裁决，其他客户端无从得知被标记者。
 */
public final class DominatorClientModule {
    private static boolean registered;

    private DominatorClientModule() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GetInstinctHighlight.EVENT.register(DominatorClientModule::highlight);
        ClientPlayNetworking.registerGlobalReceiver(DominatorForcedShotS2CPacket.ID, (payload, context) ->
                context.client().execute(() -> DominatorForcedShotClientState.lock(payload.lockTicks())));
        ClientTickEvents.END_CLIENT_TICK.register(client -> DominatorForcedShotClientState.tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> DominatorForcedShotClientState.reset());
    }

    private static @Nullable GetInstinctHighlight.HighlightResult highlight(Entity target) {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return null;
        }
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null
                || target == viewer
                || !(target instanceof PlayerEntity targetPlayer)
                || !GameFunctions.isPlayerPlayingAndAlive(viewer)
                || !GameFunctions.isPlayerPlayingAndAlive(targetPlayer)) {
            return null;
        }
        Role role = GameWorldComponent.KEY.get(viewer.getWorld()).getRole(viewer);
        if (!DominatorRules.isDominator(role)) {
            return null;
        }
        DominatorPlayerComponent component = DominatorPlayerComponent.KEY.get(viewer);
        if (!component.isMarked(targetPlayer.getUuid())) {
            return null;
        }
        return GetInstinctHighlight.HighlightResult.always(
                DominatorRules.COLOR,
                GetInstinctHighlight.HighlightResult.PRIORITY_HIGH
        );
    }
}
