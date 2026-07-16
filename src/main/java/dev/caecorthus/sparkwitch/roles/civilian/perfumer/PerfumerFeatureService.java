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
 * Publishes the Perfumer's private, always-on mark, blood, and corpse outlines.
 * 发布调香师私有且常亮的标记、血腥气味与尸体轮廓。
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
                    || GameFunctions.isPlayerSpectatingOrCreative(targetPlayer)) {
                return null;
            }

            PerfumerPlayerComponent component = PerfumerPlayerComponent.KEY.get(viewer);
            if (component.isBloody(targetPlayer.getUuid())) {
                color = PerfumerRules.BLOODY_OUTLINE_COLOR;
                if (!PerfumerRules.shouldOutlinePlayer(
                        viewer.squaredDistanceTo(targetPlayer), viewer.canSee(targetPlayer))) {
                    return null;
                }
            } else if (component.isMarked(targetPlayer.getUuid())) {
                color = PerfumerRules.ROLE_COLOR;
                if (!viewer.canSee(targetPlayer)
                        || !PerfumerRules.isWithinVisibleOutlineRange(viewer.squaredDistanceTo(targetPlayer))) {
                    return null;
                }
            } else {
                return null;
            }
        } else if (target instanceof PlayerBodyEntity body) {
            if (NoellesHiddenBodiesBridge.isHidden(viewer.getWorld(), body.getPlayerUuid())) {
                return null;
            }
            color = PerfumerRules.CORPSE_OUTLINE_COLOR;
            if (!PerfumerRules.shouldOutlinePlayer(viewer.squaredDistanceTo(body), viewer.canSee(body))) {
                return null;
            }
        } else {
            return null;
        }

        return FactionInstinctPolicy.InstinctResult.show(color, false, OUTLINE_PRIORITY);
    }
}
