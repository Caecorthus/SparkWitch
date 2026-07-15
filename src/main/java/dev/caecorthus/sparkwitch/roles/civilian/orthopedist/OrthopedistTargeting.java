package dev.caecorthus.sparkwitch.roles.civilian.orthopedist;

import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the aimed player on the authoritative server instead of trusting a client-supplied UUID.
 * 在权威服务端解析准星玩家，不信任客户端提交的 UUID。
 */
public final class OrthopedistTargeting {
    private OrthopedistTargeting() {
    }

    @Nullable
    public static ServerPlayerEntity findAimedPlayer(ServerPlayerEntity caster) {
        Vec3d start = caster.getEyePos();
        Vec3d end = start.add(caster.getRotationVec(1.0F).multiply(OrthopedistRules.TARGET_RANGE));
        EntityHitResult result = ProjectileUtil.raycast(
                caster,
                start,
                end,
                caster.getBoundingBox().stretch(caster.getRotationVec(1.0F).multiply(OrthopedistRules.TARGET_RANGE))
                        .expand(1.0D),
                entity -> entity instanceof ServerPlayerEntity target
                        && target != caster
                        && GameFunctions.isPlayerPlayingAndAlive(target),
                OrthopedistRules.TARGET_RANGE_SQUARED
        );
        if (!(result != null && result.getEntity() instanceof ServerPlayerEntity target)) {
            return null;
        }
        return isValidDirectTarget(caster, target) ? target : null;
    }

    public static boolean isValidDirectTarget(ServerPlayerEntity caster, ServerPlayerEntity target) {
        return target != caster
                && GameFunctions.isPlayerPlayingAndAlive(target)
                && caster.canSee(target)
                && caster.squaredDistanceTo(target) <= OrthopedistRules.TARGET_RANGE_SQUARED;
    }
}
