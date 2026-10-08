package dev.caecorthus.sparkwitch.mixin.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecGunRules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Owner O2: no {@code wathe:guns} item is ever inserted into a USEC's inventory, so gives and offers that go through
 * {@code insertStack} fall back to their drop path. Both overloads are guarded at HEAD, as SparkTraits guards the
 * Impostor. Server only; a creative player and a former USEC are free (see {@link UsecGunRules}).
 * 所有者 O2：任何 {@code wathe:guns} 物品都不会被放入 USEC 的背包，因此经由 {@code insertStack} 的给予与 offer 会退回到其
 * 丢出路径。两个重载都在 HEAD 拦截，与 SparkTraits 拦截内鬼的方式相同。仅服务端；创造模式玩家与前 USEC 不受限制（见
 * {@link UsecGunRules}）。
 */
@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryUsecGunMixin {
    @Shadow
    @Final
    public PlayerEntity player;

    @Inject(method = "insertStack(Lnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$blockUsecGunInsert(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (UsecGunRules.blocksGun(this.player, stack)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "insertStack(ILnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$blockUsecGunInsertIntoSlot(int slot, ItemStack stack,
                                                        CallbackInfoReturnable<Boolean> cir) {
        if (UsecGunRules.blocksGun(this.player, stack)) {
            cir.setReturnValue(false);
        }
    }
}
