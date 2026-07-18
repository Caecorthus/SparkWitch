package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaKnifeInventoryRules;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents the bound Vendetta knife from becoming a transferable item entity. */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityVendettaKnifeMixin {
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$keepVendettaKnifeBound(
            ItemStack stack,
            boolean throwRandomly,
            boolean retainOwnership,
            CallbackInfoReturnable<ItemEntity> cir
    ) {
        if (VendettaKnifeInventoryRules.blocksDrop(stack)) {
            cir.setReturnValue(null);
        }
    }
}
