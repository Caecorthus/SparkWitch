package dev.caecorthus.sparkwitch.mixin.blind;

import dev.caecorthus.sparkwitch.roles.civilian.blind.kit.BlindInventoryRules;
import net.minecraft.block.BlockState;
import net.minecraft.block.DecoratedPotBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps a held White Cane or ComTac VIII out of a decorated pot on both sides: the pot answers
 * {@code SKIP_DEFAULT_BLOCK_INTERACTION} for a bound stack, so it never takes the item and never wobbles, while vanilla
 * still runs the item's own use (cane tap, ComTac equip). Every other item and block is untouched.
 * 在双端阻止饰纹陶罐收走手持的盲杖或 ComTac VIII：陶罐对绑定物品返回 {@code SKIP_DEFAULT_BLOCK_INTERACTION}，因此既不
 * 收走物品也不晃动，而原版仍会执行物品自身的使用（敲击盲杖、戴上 ComTac）。其他物品与方块不受影响。
 */
@Mixin(DecoratedPotBlock.class)
public abstract class BlindKitDecoratedPotMixin {
    @Inject(
            method = "onUseWithItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/block/BlockState;"
                    + "Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;"
                    + "Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ItemActionResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$keepBlindKitOutOfPot(ItemStack stack, BlockState state, World world, BlockPos pos,
                                                 PlayerEntity player, Hand hand, BlockHitResult hit,
                                                 CallbackInfoReturnable<ItemActionResult> cir) {
        if (BlindInventoryRules.isBound(stack)) {
            cir.setReturnValue(ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION);
        }
    }
}
