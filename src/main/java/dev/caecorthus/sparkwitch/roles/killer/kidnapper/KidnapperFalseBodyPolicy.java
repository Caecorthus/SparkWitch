package dev.caecorthus.sparkwitch.roles.killer.kidnapper;

import dev.caecorthus.sparkwitch.compat.SparkTraitsBodyDragBridge;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/** Owns cross-mod fake-corpse rejection. / 只负责跨模组假尸体拒绝规则。 */
public final class KidnapperFalseBodyPolicy {
    private KidnapperFalseBodyPolicy() {
    }

    public static boolean canDrag(PlayerBodyEntity body) {
        return SparkTraitsBodyDragBridge.canDragBody(body) && !isCameraBoundFakeBody(body);
    }

    private static boolean isCameraBoundFakeBody(PlayerBodyEntity body) {
        if (!(body.getWorld() instanceof ServerWorld world)) {
            return true;
        }
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(body.getPlayerUuid());
        return owner != null
                && GameFunctions.isPlayerPlayingAndAlive(owner)
                && owner.getCameraEntity() == body;
    }
}
