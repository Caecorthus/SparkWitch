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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server only. Inserting a foreign gun into the Savior's inventory fails; vanilla {@code offer} ignores a refused
 * insert (the split stack would vanish), so an offered foreign gun is dropped at the Savior's feet instead. The
 * pick-from-inventory swap never pulls the hidden revolver out of the client-only locked rows.
 * 仅服务端。向救世主物品栏插入外部枪械会失败；原版 {@code offer} 会忽略被拒绝的插入（拆出的物品堆会凭空消失），因此 offer
 * 的外部枪械改为掉在救世主脚下。从背包选取的交换永远不会把隐藏左轮拉出仅客户端锁定的行。
 */
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

    @Inject(method = "offer(Lnet/minecraft/item/ItemStack;Z)V", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$dropOfferedSaviorGun(ItemStack stack, boolean notifiesClient, CallbackInfo ci) {
        if (!stack.isEmpty()
                && this.player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer
                && SaviorGunPickupRules.blocks(serverPlayer, stack)) {
            serverPlayer.dropItem(stack.copyAndEmpty(), false);
            ci.cancel();
        }
    }

    @Inject(method = "swapSlotWithHotbar(I)V", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$keepHiddenSaviorRevolver(int slot, CallbackInfo ci) {
        if (this.player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer
                && SaviorGunPickupRules.blocksHotbarSwap(serverPlayer, slot)) {
            ci.cancel();
        }
    }
}
