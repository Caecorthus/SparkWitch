package dev.caecorthus.sparkwitch.roles.killer.kidnapper;

import dev.doctor4t.wathe.api.event.CanTargetBody;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.jetbrains.annotations.Nullable;

/** Resolves the aimed corpse on the authoritative server. / 仅在权威服务端解析准星尸体。 */
public final class KidnapperTargeting {
    private KidnapperTargeting() {
    }

    @Nullable
    public static PlayerBodyEntity findAimedBody(ServerPlayerEntity player) {
        HitResult collision = ProjectileUtil.getCollision(
                player,
                entity -> entity instanceof PlayerBodyEntity body
                        && CanTargetBody.EVENT.invoker().canTarget(player, body),
                (float) KidnapperRules.TARGET_RANGE
        );
        if (!(collision instanceof EntityHitResult entityHit)
                || !(entityHit.getEntity() instanceof PlayerBodyEntity body)) {
            return null;
        }
        return player.canSee(body)
                && player.squaredDistanceTo(body) <= KidnapperRules.TARGET_RANGE_SQUARED
                ? body
                : null;
    }
}
