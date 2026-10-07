package dev.caecorthus.sparkwitch.client.hooks;

import dev.caecorthus.sparkwitch.compat.NoellesHiddenBodiesBridge;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetRules;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Outlines every body in the owner-synced, only-growing Death Sense set through Wathe's public outline event; a body
 * that despawns simply stops rendering.
 * 通过 Wathe 公共描边事件描边仅所有者同步、只增不减的死亡感知尸体集合；尸体实体消失后自然不再显示。
 */
public final class ProphetCorpseHighlightClientHooks {
    private static boolean registered;

    private ProphetCorpseHighlightClientHooks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GetInstinctHighlight.EVENT.register(ProphetCorpseHighlightClientHooks::highlightBody);
    }

    @Nullable
    private static GetInstinctHighlight.HighlightResult highlightBody(Entity target) {
        if (!SparkWitchServerConnection.isConfirmedServer()
                || !(target instanceof PlayerBodyEntity body)) {
            return null;
        }

        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null
                || !GameFunctions.isPlayerPlayingAndAlive(viewer)
                || GameFunctions.isPlayerSpectatingOrCreative(viewer)
                || !ProphetRules.isProphet(
                        GameWorldComponent.KEY.get(viewer.getWorld()).getRole(viewer))
                || NoellesHiddenBodiesBridge.isHidden(viewer.getWorld(), body.getPlayerUuid())) {
            return null;
        }

        ProphetPlayerComponent component = ProphetPlayerComponent.KEY.get(viewer);
        if (!component.isSenseBody(body.getUuid())) {
            return null;
        }
        return GetInstinctHighlight.HighlightResult.always(
                ProphetRules.CORPSE_HIGHLIGHT_COLOR,
                ProphetRules.CORPSE_HIGHLIGHT_PRIORITY
        );
    }
}
