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
                // Matches the server's GrandWitchTargeting: a spectator (a Rift Gate occupant) is skipped, so an
                // occupied gate never steals the aim from the player behind it. Game modes are public (tab list).
                // 与服务端 GrandWitchTargeting 一致：跳过旁观者（裂隙门内的玩家），被占用的门不会抢走门后玩家的准心。
                // 游戏模式本就公开（玩家列表）。
                entity -> entity instanceof PlayerEntity target
                        && target != user
                        && !target.isSpectator()
                        && GameFunctions.isPlayerPlayingAndAlive(target),
                REACH * REACH
        );
        if (result == null || !(result.getEntity() instanceof PlayerEntity target)) {
            return null;
        }
        return user.canSee(target) && user.squaredDistanceTo(target) <= REACH * REACH ? target : null;
    }
}
