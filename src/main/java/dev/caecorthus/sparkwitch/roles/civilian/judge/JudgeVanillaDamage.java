package dev.caecorthus.sparkwitch.roles.civilian.judge;

import dev.caecorthus.sparkwitch.mixin.JudgeProjectileOwnerAccessor;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/** Use the current damage's cause, never a historical attacker guess. / 只读取本次伤害来源，不猜历史攻击者。 */
public final class JudgeVanillaDamage {
    private JudgeVanillaDamage() {}

    public static UUID actor(DamageSource source) {
        if (source.getAttacker() instanceof PlayerEntity player) return player.getUuid();
        if (source.getSource() instanceof ProjectileEntity projectile) {
            return ((JudgeProjectileOwnerAccessor) projectile).sparkwitch$judgeOwnerUuid();
        }
        return null;
    }

    public static UUID actor(ServerPlayerEntity victim, DamageSource source) {
        UUID actor = actor(source);
        if (actor != null) return actor;
        // A rejected, proven train fall must not turn into an unattributed void death on the next tick.
        // 已被阻止且有确切责任人的坠车，不得下一 tick 变成无归属虚空死亡。
        return source.isOf(DamageTypes.FALL) || source.isOf(DamageTypes.OUT_OF_WORLD)
                ? JudgeTrainFallAttribution.currentCause(victim) : null;
    }

    public static boolean blocksTransition(LivingEntity entity, float nextHealth, DamageSource source) {
        return entity instanceof ServerPlayerEntity victim && entity.getHealth() > 0.0F && nextHealth <= 0.0F
                && JudgeRuntime.blocksKill(victim.getServerWorld(), actor(victim, source), victim.getUuid());
    }
}
