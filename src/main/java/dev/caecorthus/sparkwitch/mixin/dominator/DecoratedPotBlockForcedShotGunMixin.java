package dev.caecorthus.sparkwitch.mixin.dominator;

import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorForcedShotGuns;
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
 * Keeps the marked forced-shot revolver out of a decorated pot on both sides: the pot answers
 * {@code SKIP_DEFAULT_BLOCK_INTERACTION}, so it never takes the gun (the revolver's own use still runs and its
 * hand-fired shot is refused by the server). Guarding the pot itself also covers a Wathe ornament that forwards the
 * click to its pot. Every other item and block is untouched. Parallel to, never shared with, the other roles' pot
 * guards. / 在双端阻止饰纹陶罐收走强制开枪的标记左轮：陶罐返回 {@code SKIP_DEFAULT_BLOCK_INTERACTION}，因此不会收走该枪
 * （左轮自身的使用照常进行，其亲手开枪会被服务端拒绝）。在陶罐本身拦截也覆盖了把点击转发给陶罐的 Wathe 装饰物。其他物品与
 * 方块不受影响。与其他职业的陶罐拦截平行，绝不共享。
 */
@Mixin(DecoratedPotBlock.class)
public abstract class DecoratedPotBlockForcedShotGunMixin {
    @Inject(
            method = "onUseWithItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/block/BlockState;"
                    + "Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;"
                    + "Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ItemActionResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$keepForcedShotGunOutOfPot(ItemStack stack, BlockState state, World world, BlockPos pos,
                                                      PlayerEntity player, Hand hand, BlockHitResult hit,
                                                      CallbackInfoReturnable<ItemActionResult> cir) {
        if (DominatorForcedShotGuns.blocksBlockUse(stack, state)) {
            cir.setReturnValue(ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION);
        }
    }
}
