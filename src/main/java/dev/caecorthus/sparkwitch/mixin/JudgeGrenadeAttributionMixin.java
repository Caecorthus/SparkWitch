package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeKillAttribution;
import dev.doctor4t.wathe.entity.GrenadeEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Scope each actual blast victim, not projectile use or damage globally. / 只包装实际爆炸致死，不阻止投掷。 */
@Mixin(GrenadeEntity.class)
public abstract class JudgeGrenadeAttributionMixin {
    @WrapOperation(method = "onCollision", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/game/GameFunctions;killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;)V"))
    private void sparkwitch$scopeGrenade(ServerPlayerEntity victim, boolean body, ServerPlayerEntity killer,
                                        Identifier reason, Operation<Void> original) {
        JudgeKillAttribution.runWith(victim.getServerWorld(), ((JudgeProjectileOwnerAccessor) this).sparkwitch$judgeOwnerUuid(),
                () -> original.call(victim, body, killer, reason));
    }
}
