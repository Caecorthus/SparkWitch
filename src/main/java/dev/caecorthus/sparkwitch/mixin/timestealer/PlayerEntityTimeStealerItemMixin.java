package dev.caecorthus.sparkwitch.mixin.timestealer;

import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStealerItemDrops;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the Clock and Time Stamps from ever becoming item entities. {@code ServerPlayerEntity} delegates here via
 * {@code super}, so every server drop path is covered; the decision (Clock restored by the loadout, stamps emptied in
 * place and re-delivered to a living holder) lives in {@code TimeStealerItemDrops}, never in this mixin.
 * 阻止时钟与时光邮票变成物品实体。{@code ServerPlayerEntity} 通过 {@code super} 调用此方法，因此覆盖所有服务端丢弃路径；
 * 具体处理（时钟由装备服务恢复，邮票原地清空后重新发放给存活持有者）位于 {@code TimeStealerItemDrops}，而不在本 mixin 中。
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityTimeStealerItemMixin {
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$keepTimeStealerItemBound(
            ItemStack stack,
            boolean throwRandomly,
            boolean retainOwnership,
            CallbackInfoReturnable<ItemEntity> cir
    ) {
        if (TimeStealerItemDrops.intercept((PlayerEntity) (Object) this, stack)) {
            cir.setReturnValue(null);
        }
    }
}
