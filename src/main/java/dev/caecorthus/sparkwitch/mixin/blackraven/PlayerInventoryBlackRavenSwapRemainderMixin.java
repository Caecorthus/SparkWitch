package dev.caecorthus.sparkwitch.mixin.blackraven;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseService;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * No-drop swap: within the post-switch window (or while a swap runs), an offerOrDrop remainder of a bound, alive
 * Raven goes to the stash overflow instead of the floor. Every other player and moment keeps vanilla dropping.
 * 无丢弃交换：切换后窗口内（或交换进行中），绑定且存活的黑羽鸦 offerOrDrop 剩余物品进入存档溢出区，
 * 而不是掉落在地。其他玩家与其他时刻保持原版掉落。
 */
@Mixin(value = PlayerInventory.class)
public abstract class PlayerInventoryBlackRavenSwapRemainderMixin {
    @Shadow
    @Final
    public PlayerEntity player;

    @WrapOperation(
            method = "offer(Lnet/minecraft/item/ItemStack;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;dropItem(Lnet/minecraft/item/ItemStack;Z)Lnet/minecraft/entity/ItemEntity;"
            )
    )
    private ItemEntity sparkwitch$keepSwapRemainder(PlayerEntity instance, ItemStack stack, boolean retainOwnership,
                                                   Operation<ItemEntity> original) {
        if (BlackRavenDisguiseService.captureSwapRemainder(player, stack)) {
            return null;
        }
        return original.call(instance, stack, retainOwnership);
    }
}
