package dev.caecorthus.sparkwitch.client.hooks;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.OrthopedistPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.OrthopedistRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/** Adds the direct-sight Bone Setting outline documented for Orthopedist. / 接入骨科大夫说明中的视线内正骨高亮。 */
public final class OrthopedistClientHooks {
    private static boolean registered;

    private OrthopedistClientHooks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GetInstinctHighlight.EVENT.register(OrthopedistClientHooks::getBoneSettingHighlight);
    }

    private static GetInstinctHighlight.HighlightResult getBoneSettingHighlight(Entity entity) {
        if (!SparkWitchServerConnection.isConfirmedServer()
                || !(entity instanceof PlayerEntity target)) {
            return null;
        }
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null
                || viewer == target
                || !GameFunctions.isPlayerPlayingAndAlive(viewer)
                || !isOrthopedist(viewer)
                || !viewer.canSee(target)
                || !OrthopedistPlayerComponent.KEY.get(target).hasBoneSettingActive()) {
            return null;
        }
        return GetInstinctHighlight.HighlightResult.always(OrthopedistRules.COLOR);
    }

    private static boolean isOrthopedist(PlayerEntity player) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return role != null && OrthopedistRules.ROLE_ID.equals(role.identifier());
    }
}
