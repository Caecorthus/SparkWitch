package dev.caecorthus.sparkwitch.mixin.sphinxhao;

import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.gun.SphinxHaoGunInventory;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the Sphinx Hao kit from ever becoming an item entity. {@code ServerPlayerEntity} reaches this through
 * {@code super}, so every server drop path is covered. A closing screen's cursor return that was refused is handed back
 * to a living holder ({@link SphinxHaoGunInventory#keepRefusedDrop}); a dead holder's copy simply vanishes.
 * 阻止狮身豪装备变成掉落物。{@code ServerPlayerEntity} 经 {@code super} 调用此方法，因此覆盖所有服务端丢弃路径。关闭界面时被拒绝的
 * 光标退回会还给存活的持有者（{@link SphinxHaoGunInventory#keepRefusedDrop}）；已死亡持有者的副本直接消失。
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntitySphinxHaoGunMixin {
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"),
            cancellable = true)
    private void sparkwitch$keepSphinxHaoKitBound(
            ItemStack stack,
            boolean throwRandomly,
            boolean retainOwnership,
            CallbackInfoReturnable<ItemEntity> cir
    ) {
        if (SphinxHaoGunInventory.blocksDrop(stack)) {
            SphinxHaoGunInventory.keepRefusedDrop((PlayerEntity) (Object) this, stack);
            cir.setReturnValue(null);
        }
    }
}
