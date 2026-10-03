package dev.caecorthus.sparkwitch.mixin.prophet;

import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetNecrologyRules;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the Necrology from ever becoming an item entity (Q, Ctrl+Q, closing a screen with it on the cursor);
 * {@code ServerPlayerEntity} delegates here via {@code super}, so every server drop path is covered.
 * 阻止亡者名录变成掉落物（Q、Ctrl+Q、带着光标上的书关闭界面）；{@code ServerPlayerEntity} 通过 {@code super}
 * 调用此方法，因此覆盖所有服务端丢弃路径。
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityProphetNecrologyMixin {
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$keepProphetNecrologyBound(
            ItemStack stack,
            boolean throwRandomly,
            boolean retainOwnership,
            CallbackInfoReturnable<ItemEntity> cir
    ) {
        if (ProphetNecrologyRules.blocksDrop(stack)) {
            cir.setReturnValue(null);
        }
    }
}
