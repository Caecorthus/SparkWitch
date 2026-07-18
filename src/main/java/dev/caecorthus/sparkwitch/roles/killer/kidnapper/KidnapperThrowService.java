package dev.caecorthus.sparkwitch.roles.killer.kidnapper;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Server-authoritative validation and release impulse for corpse throws. / 尸体投掷的服务端权威校验与释放冲量。 */
public final class KidnapperThrowService {
    private KidnapperThrowService() {
    }

    public static void throwCarriedBody(ServerPlayerEntity player) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        PlayerBodyEntity body = KidnapperCarryState.findCarriedBody(player);
        if (!KidnapperRules.isKidnapper(role)
                || !GameFunctions.isPlayerPlayingAndAlive(player)
                || !player.isSneaking()
                || body == null
                || body.isRemoved()
                || body.getVehicle() != player) {
            return;
        }

        Vec3d velocity = throwVelocity(player.getRotationVec(1.0F));
        KidnapperDragService.release(player);
        body.setVelocity(velocity);
        body.velocityModified = true;
    }

    static Vec3d throwVelocity(Vec3d viewVector) {
        Vec3d scaled = viewVector.multiply(KidnapperRules.THROW_SPEED);
        return new Vec3d(
                scaled.x,
                Math.max(scaled.y, KidnapperRules.THROW_MIN_UPWARD_VELOCITY),
                scaled.z
        );
    }
}
