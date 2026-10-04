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
                // Spectators are transparent: a Rift Gate occupant is an ALIVE spectator (Riftwalker D3) that is
                // never a target nor a shield for the player behind its gate; Wathe's liveness ignores the game mode.
                // 旁观者是透明的：裂隙门内的玩家是存活旁观者（隙行者 D3），既不是目标，也不会挡住门后的玩家；
                // Wathe 的存活检查不看游戏模式。
                entity -> entity instanceof ServerPlayerEntity target
                        && target != caster
                        && !target.isSpectator()
                        && GameFunctions.isPlayerPlayingAndAlive(target),
                OrthopedistRules.TARGET_RANGE_SQUARED
        );
        if (!(result != null && result.getEntity() instanceof ServerPlayerEntity target)) {
            return null;
        }
        return isValidDirectTarget(caster, target) ? target : null;
    }

    /** The last server check before Bone Setting; rejects spectators too. / 正骨前最后的服务端校验，同样拒绝旁观者。 */
    public static boolean isValidDirectTarget(ServerPlayerEntity caster, ServerPlayerEntity target) {
        return target != caster
                && !target.isSpectator()
                && GameFunctions.isPlayerPlayingAndAlive(target)
                && caster.canSee(target)
                && caster.squaredDistanceTo(target) <= OrthopedistRules.TARGET_RANGE_SQUARED;
    }
}
