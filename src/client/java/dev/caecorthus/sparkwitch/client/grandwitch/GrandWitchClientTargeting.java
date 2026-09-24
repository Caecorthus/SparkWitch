package dev.caecorthus.sparkwitch.client.grandwitch;

import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** Eight-block ability aim, not vanilla's three-block melee crosshair. / 八格技能准心，不复用原版三格近战目标。 */
public final class GrandWitchClientTargeting {
    private static final double REACH = 8.0D;

    private GrandWitchClientTargeting() {
    }

    public static @Nullable PlayerEntity findAimedPlayer(PlayerEntity user) {
        Vec3d start = user.getEyePos();
        Vec3d look = user.getRotationVec(1.0F);
        EntityHitResult result = ProjectileUtil.raycast(
                user,
                start,
                start.add(look.multiply(REACH)),
                user.getBoundingBox().stretch(look.multiply(REACH)).expand(1.0D),
                entity -> entity instanceof PlayerEntity target
                        && target != user
                        && GameFunctions.isPlayerPlayingAndAlive(target),
                REACH * REACH
        );
        if (result == null || !(result.getEntity() instanceof PlayerEntity target)) {
            return null;
        }
        return user.canSee(target) && user.squaredDistanceTo(target) <= REACH * REACH ? target : null;
    }
}
