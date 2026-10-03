package dev.caecorthus.sparkwitch.mixin.prophet;

import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetNecrologyRules;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Excludes the Necrology from Wathe's death-drop loop; the {@code KillPlayer.AFTER} cleanup deletes it instead.
 * Coexists with the other HEAD guards on {@code shouldDropOnDeath}: each only answers for its own items.
 * 将亡者名录排除出 Wathe 死亡掉落流程，改由 {@code KillPlayer.AFTER} 清理删除。与 {@code shouldDropOnDeath} 上的其他
 * HEAD 守卫共存：每个守卫只对自己的物品作答。
 */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsProphetNecrologyDropMixin {
    @Inject(method = "shouldDropOnDeath", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$excludeProphetNecrology(
            ItemStack stack,
            PlayerEntity victim,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (ProphetNecrologyRules.blocksDeathDrop(stack)) {
            cir.setReturnValue(false);
        }
    }
}
