package dev.caecorthus.sparkwitch.mixin.hanna;

import dev.caecorthus.sparkwitch.roles.civilian.hanna.HannaService;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A thrown object breaks 汉娜's float, the same way a knife swing does.
 * <p>Server side only: the callback fires where the hit is resolved, so no client prediction is involved.
 * 投掷物命中同样会打断汉娜的浮游，和刀的挥击一致。
 * <p>只在服务端处理：回调发生在命中结算处，不涉及客户端预测。
 */
@Mixin(ProjectileEntity.class)
public abstract class HannaProjectileMixin {

    @Inject(method = "onEntityHit", at = @At("HEAD"))
    private void sparkwitch$interruptFloatOnProjectileHit(EntityHitResult hitResult, CallbackInfo ci) {
        if (!(hitResult.getEntity() instanceof ServerPlayerEntity target)) {
            return;
        }
        if (!HannaService.isFloating(target)) {
            return;
        }
        Entity owner = ((ProjectileEntity) (Object) this).getOwner();
        HannaService.interrupt(target, owner instanceof ServerPlayerEntity p ? p : null);
    }
}
