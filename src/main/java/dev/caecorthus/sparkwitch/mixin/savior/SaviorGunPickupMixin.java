package dev.caecorthus.sparkwitch.mixin.savior;

import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorGunPickupRules;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Savior may not pick guns off the ground; the hidden revolver is placed by direct slot writes, so it is unaffected. / 救世主不能捡地面的枪。 */
@Mixin(ItemEntity.class)
public abstract class SaviorGunPickupMixin {
    @Shadow
    public abstract ItemStack getStack();

    @Inject(method = "onPlayerCollision(Lnet/minecraft/entity/player/PlayerEntity;)V", at = @At("HEAD"),
            cancellable = true)
    private void sparkwitch$blockSaviorGunPickup(PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity serverPlayer
                && SaviorGunPickupRules.blocks(serverPlayer, this.getStack())) {
            ci.cancel();
        }
    }
}
