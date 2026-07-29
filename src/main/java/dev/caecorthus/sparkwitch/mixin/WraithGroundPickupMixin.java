package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithParticipationRules;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops server-authoritative ground pickup before vanilla and Wathe insert the stack.
 * 在原版与 Wathe 写入背包前，阻止激活中的冤魂捡取地面物品。
 */
@Mixin(value = ItemEntity.class, priority = 1500)
public abstract class WraithGroundPickupMixin {
    @Inject(
            method = "onPlayerCollision(Lnet/minecraft/entity/player/PlayerEntity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$preventActiveWraithPickup(PlayerEntity player, CallbackInfo ci) {
        if (!player.getWorld().isClient
                && !WraithParticipationRules.mayPickUpGroundItems(WraithStateService.isActive(player))) {
            ci.cancel();
        }
    }
}
