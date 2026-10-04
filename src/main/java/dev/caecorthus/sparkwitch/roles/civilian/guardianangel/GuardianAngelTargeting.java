package dev.caecorthus.sparkwitch.roles.civilian.guardianangel;

import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** Authoritative three-block player raycast. / 服务端权威的三格玩家射线判定。 */
public final class GuardianAngelTargeting {
    private GuardianAngelTargeting() {
    }

    public static @Nullable ServerPlayerEntity findAimedPlayer(ServerPlayerEntity caster) {
        Vec3d look = caster.getRotationVec(1.0F);
        Vec3d start = caster.getEyePos();
        Vec3d end = start.add(look.multiply(GuardianAngelRules.TARGET_RANGE));
        EntityHitResult result = ProjectileUtil.raycast(
                caster,
                start,
                end,
                caster.getBoundingBox().stretch(look.multiply(GuardianAngelRules.TARGET_RANGE)).expand(1.0D),
                // Spectators are transparent: a Rift Gate occupant (an ALIVE spectator, Riftwalker D3) never takes
                // the aim nor shields the player behind its gate. SFA canAffectPlayer is not used: the caster is an
                // active Wraith, which it denies outright.
                // 旁观者是透明的：裂隙门内的玩家（存活旁观者，隙行者 D3）既不会被选中，也不会挡住门后的玩家。
                // 不使用 SFA 的 canAffectPlayer：施法者是激活的冤魂，会被它直接拒绝。
                entity -> entity instanceof ServerPlayerEntity target
                        && target != caster
                        && !target.isSpectator()
                        && GameFunctions.isPlayerPlayingAndAlive(target),
                GuardianAngelRules.TARGET_RANGE_SQUARED
        );
        if (result == null || !(result.getEntity() instanceof ServerPlayerEntity target)) {
            return null;
        }
        return GuardianAngelRules.canTarget(
                target == caster,
                GameFunctions.isPlayerPlayingAndAlive(target),
                target.isSpectator(),
                false,
                caster.canSee(target),
                caster.squaredDistanceTo(target)
        ) ? target : null;
    }

    public static boolean isValidDirectTarget(ServerPlayerEntity caster, ServerPlayerEntity target) {
        return GuardianAngelRules.canTarget(
                target == caster,
                GameFunctions.isPlayerPlayingAndAlive(target),
                target.isSpectator(),
                target.hasStatusEffect(GuardianAngelEffects.guardianShield()),
                caster.canSee(target),
                caster.squaredDistanceTo(target)
        );
    }
}
