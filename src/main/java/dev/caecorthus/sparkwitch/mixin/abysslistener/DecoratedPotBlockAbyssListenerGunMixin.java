package dev.caecorthus.sparkwitch.mixin.abysslistener;

import dev.caecorthus.sparkwitch.roles.witch.abysslistener.loadout.AbyssListenerInventoryRules;
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
 * Keeps a held Shriek Gun out of a decorated pot on both sides: the pot answers {@code SKIP_DEFAULT_BLOCK_INTERACTION}
 * for the gun, so it never takes the item and never wobbles, while vanilla still runs the gun's own use (it fires).
 * Guarding the pot itself also covers a Wathe ornament that forwards the click to the pot it hangs on, which a
 * {@code UseBlockCallback} reading the hit block would miss (and its FAIL would end the whole right-click). Every other
 * item and block is untouched. Parallel to, never shared with, the Time Stealer and Blind kit pot guards.
 * 在双端阻止饰纹陶罐收走手持的啸音铳：陶罐对枪返回 {@code SKIP_DEFAULT_BLOCK_INTERACTION}，因此既不收走物品也不晃动，
 * 而原版仍会执行枪自身的使用（照常开火）。在陶罐本身拦截也覆盖了把点击转发给所挂陶罐的 Wathe 装饰物，读取命中方块的
 * {@code UseBlockCallback} 会漏掉这种情况（且其 FAIL 会终止整次右键）。其他物品与方块不受影响。
 * 与窃时者及盲人装备的陶罐防护平行，绝不共享。
 */
@Mixin(DecoratedPotBlock.class)
public abstract class DecoratedPotBlockAbyssListenerGunMixin {
    @Inject(
            method = "onUseWithItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/block/BlockState;"
                    + "Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;"
                    + "Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ItemActionResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$keepShriekGunOutOfPot(ItemStack stack, BlockState state, World world, BlockPos pos,
                                                  PlayerEntity player, Hand hand, BlockHitResult hit,
                                                  CallbackInfoReturnable<ItemActionResult> cir) {
        if (AbyssListenerInventoryRules.blocksBlockUse(stack, state)) {
            cir.setReturnValue(ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION);
        }
    }
}
