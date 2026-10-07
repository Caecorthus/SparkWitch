package dev.caecorthus.sparkwitch.roles.killer.kidnapper;

import dev.caecorthus.sparkwitch.compat.SparkTraitsBodyDragBridge;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianDecoyBodies;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/** Owns cross-mod fake-corpse rejection. / 只负责跨模组假尸体拒绝规则。 */
public final class KidnapperFalseBodyPolicy {
    private KidnapperFalseBodyPolicy() {
    }

    public static boolean canDrag(PlayerBodyEntity body) {
        return !isMagicianDecoy(body)
                && SparkTraitsBodyDragBridge.canDragBody(body)
                && !isCameraBoundFakeBody(body);
    }

    /**
     * Owner decision 2026-10-07 D2: a Magician decoy body is never dragged, and the refusal names it as an illusion.
     * Server-only answer (the decoy registry is never synced).
     * 所有者 2026-10-07 决定 D2：魔术师的诱饵尸体永远不能被拖动，拒绝提示会点明它是幻象。仅服务端可判定（诱饵登记表从不同步）。
     */
    public static boolean isMagicianDecoy(PlayerBodyEntity body) {
        return MagicianDecoyBodies.isDecoy(body);
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
