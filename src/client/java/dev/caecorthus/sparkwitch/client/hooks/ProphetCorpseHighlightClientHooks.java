package dev.caecorthus.sparkwitch.client.hooks;

import dev.caecorthus.sparkwitch.compat.NoellesHiddenBodiesBridge;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetRules;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** Publishes owner-synced Death Omen bodies through Wathe's public outline event. / 通过 Wathe 公共描边事件显示仅所有者同步的死亡预兆尸体。 */
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

        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(viewer);
        if (!component.isDeathOmenActive() || !component.isDeathOmenBody(body.getUuid())) {
            return null;
        }
        return GetInstinctHighlight.HighlightResult.always(
                ProphetRules.CORPSE_HIGHLIGHT_COLOR,
                ProphetRules.CORPSE_HIGHLIGHT_PRIORITY
        );
    }
}
