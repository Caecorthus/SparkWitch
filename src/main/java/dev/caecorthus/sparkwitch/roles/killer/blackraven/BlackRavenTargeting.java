package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** Resolves Feather Blade targets from authoritative server position and look direction. */
public final class BlackRavenTargeting {
    private BlackRavenTargeting() {
    }

    public static @Nullable ServerPlayerEntity findAimedPlayer(ServerPlayerEntity user) {
        Vec3d start = user.getEyePos();
        Vec3d look = user.getRotationVec(1.0F);
        Vec3d end = start.add(look.multiply(BlackRavenRules.FEATHER_REACH));
        EntityHitResult result = ProjectileUtil.raycast(
                user,
                start,
                end,
                user.getBoundingBox().stretch(look.multiply(BlackRavenRules.FEATHER_REACH)).expand(1.0D),
                entity -> entity instanceof ServerPlayerEntity target
                        && target != user
                        && GameFunctions.isPlayerPlayingAndAlive(target),
                BlackRavenRules.FEATHER_REACH * BlackRavenRules.FEATHER_REACH
        );
        if (result == null || !(result.getEntity() instanceof ServerPlayerEntity target)) {
            return null;
        }
        return user.canSee(target) && user.squaredDistanceTo(target) <= BlackRavenRules.FEATHER_REACH * BlackRavenRules.FEATHER_REACH
                ? target
                : null;
    }
}
