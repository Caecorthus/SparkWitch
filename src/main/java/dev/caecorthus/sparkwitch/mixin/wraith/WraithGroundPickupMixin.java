package dev.caecorthus.sparkwitch.mixin.wraith;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks only vanilla ground-item collision pickup for every active Wraith.
 * 仅阻止所有激活冤魂通过原版地面物品碰撞拾取。
 */
@Mixin(value = ItemEntity.class, priority = 1500)
public abstract class WraithGroundPickupMixin {
    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$blockActiveWraithGroundPickup(PlayerEntity player, CallbackInfo ci) {
        if (!player.getWorld().isClient && WraithStateService.isActive(player)) {
            // Shop delivery and direct inventory insertion do not enter ItemEntity collision.
            // 商店发放与直接入包不会进入 ItemEntity 碰撞路径。
            ci.cancel();
        }
    }
}
