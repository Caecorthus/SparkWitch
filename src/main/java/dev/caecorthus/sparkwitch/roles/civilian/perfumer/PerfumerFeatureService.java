package dev.caecorthus.sparkwitch.roles.civilian.perfumer;

import dev.caecorthus.sparkfactionapi.api.FactionInstinctPolicy;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.component.PerfumerPlayerComponent;
import dev.caecorthus.sparkwitch.compat.NoellesHiddenBodiesBridge;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Publishes the Perfumer's private, always-on blood and corpse outlines.
 * 发布调香师私有且常亮的血腥气味与尸体轮廓。
 */
public final class PerfumerFeatureService {
    private static final int OUTLINE_PRIORITY = 300;
    private static boolean registered;

    private PerfumerFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        SparkFactionApi.registerInstinctPolicy(PerfumerFeatureService::instinctHighlight);
    }

    private static FactionInstinctPolicy.InstinctResult instinctHighlight(
            PlayerEntity viewer,
            Entity target,
            GameWorldComponent gameComponent
    ) {
        if (!PerfumerRuntime.isPerfumerRole(gameComponent.getRole(viewer))
                || !GameFunctions.isPlayerPlayingAndAlive(viewer)
                || GameFunctions.isPlayerSpectatingOrCreative(viewer)) {
            return null;
        }

        int color;
        if (target instanceof PlayerEntity targetPlayer) {
            if (!GameFunctions.isPlayerPlayingAndAlive(targetPlayer)
                    || GameFunctions.isPlayerSpectatingOrCreative(targetPlayer)
                    || !PerfumerPlayerComponent.KEY.get(viewer).isBloody(targetPlayer.getUuid())) {
                return null;
            }
            color = PerfumerRules.BLOODY_OUTLINE_COLOR;
        } else if (target instanceof PlayerBodyEntity body) {
            if (NoellesHiddenBodiesBridge.isHidden(viewer.getWorld(), body.getPlayerUuid())) {
                return null;
            }
            color = PerfumerRules.CORPSE_OUTLINE_COLOR;
        } else {
            return null;
        }

        if (!PerfumerRules.shouldOutlinePlayer(viewer.squaredDistanceTo(target), viewer.canSee(target))) {
            return null;
        }
        return FactionInstinctPolicy.InstinctResult.show(color, false, OUTLINE_PRIORITY);
    }
}
