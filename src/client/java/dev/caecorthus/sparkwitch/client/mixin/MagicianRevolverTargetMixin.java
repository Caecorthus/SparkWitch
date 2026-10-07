package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.item.RevolverItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 让 Wathe 左轮客户端准星可以选中魔术师皮套。 */
@Mixin(RevolverItem.class)
public abstract class MagicianRevolverTargetMixin {
    @Inject(method = "getGunTarget", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$target(PlayerEntity user, CallbackInfoReturnable<HitResult> cir) {
        HitResult hit = ProjectileUtil.getCollision(user, e -> e instanceof MagicianPlaybackEntity || (e instanceof PlayerEntity p && GameFunctions.isPlayerAliveAndSurvival(p)), 30f);
        if (hit instanceof EntityHitResult entity && entity.getEntity() instanceof MagicianPlaybackEntity) cir.setReturnValue(hit);
    }
}
