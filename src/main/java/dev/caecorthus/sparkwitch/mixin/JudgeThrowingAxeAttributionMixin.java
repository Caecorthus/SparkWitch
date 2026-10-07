package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeKillAttribution;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.entity.ThrowingAxeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Offline axe throwers remain responsible; penetration and velocity stay vanilla. / 离线飞斧仍有归属，贯穿与速度不变。 */
@Mixin(ThrowingAxeEntity.class)
public abstract class JudgeThrowingAxeAttributionMixin {
    @WrapOperation(method = "onEntityHit", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/game/GameFunctions;killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;)V"))
    private void sparkwitch$scopeAxe(ServerPlayerEntity victim, boolean body, ServerPlayerEntity killer,
                                    Identifier reason, Operation<Void> original) {
        JudgeKillAttribution.runWith(victim.getServerWorld(), ((JudgeProjectileOwnerAccessor) this).sparkwitch$judgeOwnerUuid(),
                () -> original.call(victim, body, killer, reason));
    }
}
