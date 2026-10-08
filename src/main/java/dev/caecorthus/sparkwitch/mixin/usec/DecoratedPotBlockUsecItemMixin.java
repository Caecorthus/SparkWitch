package dev.caecorthus.sparkwitch.mixin.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecInventoryRules;
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
 * Keeps a held USEC bound item out of a decorated pot on both sides: the pot answers
 * {@code SKIP_DEFAULT_BLOCK_INTERACTION}, so it never takes the item and never wobbles, while vanilla still runs the
 * item's own use (the rifle scope). Guarding the pot itself also covers a Wathe ornament that forwards the click to the
 * pot it hangs on. Every other item and block is untouched. Parallel to, never shared with, the Potion Gunner pot mixin.
 * 在双端阻止饰纹陶罐收走手持的 USEC 绑定物品：陶罐返回 {@code SKIP_DEFAULT_BLOCK_INTERACTION}，因此既不收走物品也不晃动，而原版
 * 仍会执行物品自身的使用（步枪开镜）。在陶罐本身拦截也覆盖了把点击转发给所挂陶罐的 Wathe 装饰物。其他物品与方块不受影响。
 * 与药炮手的陶罐 mixin 平行，绝不共享。
 */
@Mixin(DecoratedPotBlock.class)
public abstract class DecoratedPotBlockUsecItemMixin {
    @Inject(
            method = "onUseWithItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/block/BlockState;"
                    + "Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;"
                    + "Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ItemActionResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$keepUsecItemOutOfPot(ItemStack stack, BlockState state, World world, BlockPos pos,
                                                 PlayerEntity player, Hand hand, BlockHitResult hit,
                                                 CallbackInfoReturnable<ItemActionResult> cir) {
        if (UsecInventoryRules.blocksBlockUse(stack, state)) {
            cir.setReturnValue(ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION);
        }
    }
}
