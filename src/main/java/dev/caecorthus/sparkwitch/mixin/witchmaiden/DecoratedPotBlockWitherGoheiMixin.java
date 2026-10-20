package dev.caecorthus.sparkwitch.mixin.witchmaiden;

import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.WitherGoheiInventoryRules;
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
 * Keeps a held Wither Gohei out of a decorated pot on both sides: the pot answers
 * {@code SKIP_DEFAULT_BLOCK_INTERACTION} for it. Potion Gunner pattern.
 * 在双端阻止饰纹陶罐收走手持的凋灵御币：陶罐对它返回 {@code SKIP_DEFAULT_BLOCK_INTERACTION}。沿用药炮手的做法。
 */
@Mixin(DecoratedPotBlock.class)
public abstract class DecoratedPotBlockWitherGoheiMixin {
    @Inject(
            method = "onUseWithItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/block/BlockState;"
                    + "Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;"
                    + "Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ItemActionResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$keepWitherGoheiItemOutOfPot(ItemStack stack, BlockState state, World world, BlockPos pos,
                                                         PlayerEntity player, Hand hand, BlockHitResult hit,
                                                         CallbackInfoReturnable<ItemActionResult> cir) {
        if (WitherGoheiInventoryRules.blocksBlockUse(stack, state)) {
            cir.setReturnValue(ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION);
        }
    }
}
