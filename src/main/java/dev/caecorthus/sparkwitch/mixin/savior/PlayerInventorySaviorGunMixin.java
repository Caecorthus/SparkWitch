package dev.caecorthus.sparkwitch.mixin.savior;

import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorGunPickupRules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives/offers that insert a gun into the Savior's inventory fail and fall back to their drop path. / 给救世主放入枪械的给予/offer 退回丢出。 */
@Mixin(PlayerInventory.class)
public abstract class PlayerInventorySaviorGunMixin {
    @Shadow
    @Final
    public PlayerEntity player;

    @Inject(method = "insertStack(Lnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$blockSaviorGunInsert(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (this.player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer
                && SaviorGunPickupRules.blocks(serverPlayer, stack)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "insertStack(ILnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$blockSaviorGunInsertIntoSlot(int slot, ItemStack stack,
                                                         CallbackInfoReturnable<Boolean> cir) {
        if (this.player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer
                && SaviorGunPickupRules.blocks(serverPlayer, stack)) {
            cir.setReturnValue(false);
        }
    }
}
