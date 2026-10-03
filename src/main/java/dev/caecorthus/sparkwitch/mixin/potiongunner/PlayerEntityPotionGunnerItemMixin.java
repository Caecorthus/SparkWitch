package dev.caecorthus.sparkwitch.mixin.potiongunner;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerLoadoutService;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the launcher and shells from ever becoming item entities. {@code ServerPlayerEntity} delegates here via
 * {@code super}, so every server drop path (cursor on close with a full inventory, creative drops, offer fallbacks) is
 * covered; the decision (move semantics, re-insert for a living holder) lives in
 * {@code PotionGunnerLoadoutService#interceptDrop}, never in this mixin.
 * 阻止炮筒与炮弹变成物品实体。{@code ServerPlayerEntity} 通过 {@code super} 调用此方法，因此覆盖所有服务端丢弃路径
 * （背包已满时关闭界面的光标、创造模式丢弃、放入失败的回退）；具体处理（移动语义、为存活持有者放回）位于
 * {@code PotionGunnerLoadoutService#interceptDrop}，而不在本 mixin 中。
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityPotionGunnerItemMixin {
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$keepPotionGunnerItemBound(
            ItemStack stack,
            boolean throwRandomly,
            boolean retainOwnership,
            CallbackInfoReturnable<ItemEntity> cir
    ) {
        if (PotionGunnerLoadoutService.interceptDrop((PlayerEntity) (Object) this, stack)) {
            cir.setReturnValue(null);
        }
    }
}
