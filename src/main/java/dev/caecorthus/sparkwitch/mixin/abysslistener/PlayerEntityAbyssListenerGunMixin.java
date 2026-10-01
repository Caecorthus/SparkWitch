package dev.caecorthus.sparkwitch.mixin.abysslistener;

import dev.caecorthus.sparkwitch.roles.witch.abysslistener.loadout.AbyssListenerInventoryRules;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the Shriek Gun from ever becoming an item entity. {@code ServerPlayerEntity} delegates here via {@code super},
 * so every server drop path is covered; a caller that already removed the stack (a cursor drop on close, a full
 * inventory offer) loses it, and the loadout sweep restores the single gun without touching its cooldown.
 * 阻止啸音铳变成物品实体。{@code ServerPlayerEntity} 通过 {@code super} 调用此方法，因此覆盖所有服务端丢弃路径；
 * 已先把物品堆移出的调用方（关闭界面时丢弃光标、背包已满时的放入）会失去它，再由装备清理补回唯一的枪且不改动冷却。
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityAbyssListenerGunMixin {
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$keepShriekGunBound(
            ItemStack stack,
            boolean throwRandomly,
            boolean retainOwnership,
            CallbackInfoReturnable<ItemEntity> cir
    ) {
        if (AbyssListenerInventoryRules.blocksDrop(stack)) {
            cir.setReturnValue(null);
        }
    }
}
