package dev.caecorthus.sparkwitch.mixin.witchmaiden;

import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.WitherGoheiLoadoutService;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the Wither Gohei from ever becoming an item entity. {@code ServerPlayerEntity} delegates here via
 * {@code super}, so every server drop path is covered.
 * 阻止凋灵御币变成物品实体。{@code ServerPlayerEntity} 通过 {@code super} 调用此方法，因此覆盖所有服务端丢弃路径。
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityWitherGoheiMixin {
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$keepWitherGoheiItemBound(
            ItemStack stack,
            boolean throwRandomly,
            boolean retainOwnership,
            CallbackInfoReturnable<ItemEntity> cir
    ) {
        if (WitherGoheiLoadoutService.interceptDrop((PlayerEntity) (Object) this, stack)) {
            cir.setReturnValue(null);
        }
    }
}
