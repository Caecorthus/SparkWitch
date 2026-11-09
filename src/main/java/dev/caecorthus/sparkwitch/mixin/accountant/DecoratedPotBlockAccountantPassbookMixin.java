package dev.caecorthus.sparkwitch.mixin.accountant;

import dev.caecorthus.sparkwitch.roles.civilian.accountant.AccountantPassbookService;
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
 * Keeps the 存折 passbook out of a decorated pot on both sides: the pot answers
 * {@code SKIP_DEFAULT_BLOCK_INTERACTION}, so it never takes the passbook (whose own right-click use still runs). A pot
 * keeps its contents through Wathe's round reset, so parked coins would otherwise reach the next round. Guarding the
 * pot itself also covers a Wathe ornament that forwards the click to its pot. Every other item and block is
 * untouched. Parallel to, never shared with, the other roles' pot guards.
 * 在双端阻止饰纹陶罐收走存折：陶罐返回 {@code SKIP_DEFAULT_BLOCK_INTERACTION}，因此不会收走存折（存折自身的右键使用照常
 * 进行）。陶罐的内容能躲过 Wathe 的回合重置，否则存进去的钱会带到下一局。在陶罐本身拦截也覆盖了把点击转发给陶罐的 Wathe
 * 装饰物。其他物品与方块不受影响。与其他职业的陶罐拦截平行，绝不共享。
 */
@Mixin(DecoratedPotBlock.class)
public abstract class DecoratedPotBlockAccountantPassbookMixin {
    @Inject(
            method = "onUseWithItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/block/BlockState;"
                    + "Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;"
                    + "Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ItemActionResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$keepPassbookOutOfPot(ItemStack stack, BlockState state, World world, BlockPos pos,
                                                 PlayerEntity player, Hand hand, BlockHitResult hit,
                                                 CallbackInfoReturnable<ItemActionResult> cir) {
        if (AccountantPassbookService.blocksBlockUse(stack, state)) {
            cir.setReturnValue(ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION);
        }
    }
}
