package dev.caecorthus.sparkwitch.mixin.blind;

import dev.caecorthus.sparkwitch.roles.civilian.blind.kit.BlindItemDrops;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the White Cane and ComTac VIII from ever becoming item entities. {@code ServerPlayerEntity} delegates here via
 * {@code super}, so every server drop path is covered; the decision (cane restored by the loadout, ComTac emptied in
 * place and re-delivered to a living Blind) lives in {@code BlindItemDrops}, never in this mixin.
 * 阻止盲杖与 ComTac VIII 变成物品实体。{@code ServerPlayerEntity} 通过 {@code super} 调用此方法，因此覆盖所有服务端
 * 丢弃路径；具体处理（盲杖由装备服务恢复，ComTac 原地清空后重新交给存活的盲人）位于 {@code BlindItemDrops}，
 * 而不在本 mixin 中。
 */
@Mixin(PlayerEntity.class)
public abstract class BlindKitPlayerDropMixin {
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"),
            cancellable = true)
    private void sparkwitch$keepBlindKitBound(
            ItemStack stack,
            boolean throwRandomly,
            boolean retainOwnership,
            CallbackInfoReturnable<ItemEntity> cir
    ) {
        if (BlindItemDrops.intercept((PlayerEntity) (Object) this, stack)) {
            cir.setReturnValue(null);
        }
    }
}
