package dev.caecorthus.sparkwitch.mixin.bellringer;

import dev.caecorthus.sparkwitch.roles.killer.bellringer.BellRingerInventoryRules;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents the bound bell from becoming an item entity (mirrors the Black Raven ledger guard).
 * {@code ServerPlayerEntity} delegates here via {@code super}, so every server drop path is covered; a
 * bell already taken out of the inventory by the caller is restored by {@code BellRingerLoadoutService}.
 * 阻止绑定之钟变成物品实体（与黑羽鸦感知册的拦截一致）。{@code ServerPlayerEntity} 通过 {@code super}
 * 调用此方法，因此覆盖所有服务端丢弃路径；调用方已移出背包的钟由 {@code BellRingerLoadoutService} 恢复。
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityTollBellMixin {
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$keepTollBellBound(
            ItemStack stack,
            boolean throwRandomly,
            boolean retainOwnership,
            CallbackInfoReturnable<ItemEntity> cir
    ) {
        if (BellRingerInventoryRules.blocksDrop(stack)) {
            cir.setReturnValue(null);
        }
    }
}
