package dev.caecorthus.sparkwitch.mixin.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecInventoryRules;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Excludes the five USEC bound items from Wathe's death-drop loop, whatever any {@code ShouldDropOnDeath} listener
 * answers; the victim's copies are then deleted by the {@code KillPlayer.AFTER} cleanup in {@code UsecDeathDrops}
 * instead of landing on the floor. Server-only: {@code killPlayer} never runs on the client.
 * 将五种 USEC 绑定物品排除出 Wathe 死亡掉落流程，无论任何 {@code ShouldDropOnDeath} 监听器如何回答；死者身上的这些物品随后由
 * {@code UsecDeathDrops} 中的 {@code KillPlayer.AFTER} 清理删除，而不会掉落在地。仅服务端：客户端从不执行 {@code killPlayer}。
 */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsUsecItemDropMixin {
    @Inject(method = "shouldDropOnDeath", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$excludeUsecItems(
            ItemStack stack,
            PlayerEntity victim,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (UsecInventoryRules.blocksDeathDrop(stack)) {
            cir.setReturnValue(false);
        }
    }
}
