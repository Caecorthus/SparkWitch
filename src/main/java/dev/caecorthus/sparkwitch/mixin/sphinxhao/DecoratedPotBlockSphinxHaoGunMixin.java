package dev.caecorthus.sparkwitch.mixin.sphinxhao;

import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.gun.SphinxHaoGunInventory;
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
 * Keeps a held Sphinx Hao kit item out of a decorated pot on both sides: the pot answers
 * {@code SKIP_DEFAULT_BLOCK_INTERACTION}, so it never takes the item, while vanilla still runs the item's own use (the
 * gun fires or loads). Guarding the pot itself also covers a Wathe ornament that forwards the click to its pot.
 * Parallel to, never shared with, the other roles' pot guards.
 * 在双端阻止饰纹陶罐收走手持的狮身豪装备：陶罐返回 {@code SKIP_DEFAULT_BLOCK_INTERACTION}，因此不会收走物品，而原版仍执行物品
 * 自身的使用（手枪照常开火或装填）。在陶罐本身拦截也覆盖把点击转发给所挂陶罐的 Wathe 装饰物。与其他职业的陶罐防护平行，绝不共享。
 */
@Mixin(DecoratedPotBlock.class)
public abstract class DecoratedPotBlockSphinxHaoGunMixin {
    @Inject(
            method = "onUseWithItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/block/BlockState;"
                    + "Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;"
                    + "Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ItemActionResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$keepSphinxHaoKitOutOfPot(ItemStack stack, BlockState state, World world, BlockPos pos,
                                                     PlayerEntity player, Hand hand, BlockHitResult hit,
                                                     CallbackInfoReturnable<ItemActionResult> cir) {
        if (SphinxHaoGunInventory.blocksBlockUse(stack, state)) {
            cir.setReturnValue(ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION);
        }
    }
}
