package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithCollisionRules;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Covers the living-entity push path for Wraith-transparent players.
 * 覆盖冤魂透明玩家参与的 LivingEntity 推挤路径。
 *
 * <p>玩家互相挡路时，底层不只会问 {@code Entity.collidesWith}，还可能走到
 * {@code LivingEntity.isPushable} 与 {@code LivingEntity.pushAway(Entity)}。
 * 这里作为 SparkFactionAPI 碰撞豁免的本地兜底：只要任一方是冤魂透明态，就取消推挤。
 * “透明态”同时接受 active 冤魂状态和 {@code noellesroles:no_collision} 同步状态效果，
 * 避免其他玩家客户端因为读不到目标玩法组件而仍然把冤魂当成实体墙。</p>
 *
 * <p>priority 与实体碰撞 mixin 一样保持较低，确保本地兜底晚于 Wathe/NoellesRoles 的默认碰撞包装生效。</p>
 */
@Mixin(value = LivingEntity.class, priority = 100)
public abstract class WraithLivingEntityCollisionMixin {
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$makeWraithTransparentUnpushable(CallbackInfoReturnable<Boolean> cir) {
        if (WraithCollisionRules.isCollisionTransparent((Entity) (Object) this)) {
            // 冤魂透明态实体不应作为可推动实体，否则普通玩家贴近时仍可能被体积反推。
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "pushAway(Lnet/minecraft/entity/Entity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$skipWraithPlayerPush(Entity other, CallbackInfo ci) {
        if (WraithCollisionRules.shouldIgnorePlayerBodyCollision((Entity) (Object) this, other)) {
            // 取消双方推挤兜底，避免普通存活玩家穿过冤魂时被重新弹开。
            ci.cancel();
        }
    }
}
