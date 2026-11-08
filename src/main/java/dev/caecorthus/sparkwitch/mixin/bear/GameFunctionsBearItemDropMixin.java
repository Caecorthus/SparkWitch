package dev.caecorthus.sparkwitch.mixin.bear;

import dev.caecorthus.sparkwitch.roles.civilian.bear.BearLoadoutService;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Excludes the four BEAR items from Wathe's death-drop loop, whatever any {@code ShouldDropOnDeath} listener answers
 * (the USEC precedent); the victim's copies are then deleted by BEAR's {@code KillPlayer.AFTER} cleanup instead of
 * landing on the floor. Server only: {@code killPlayer} never runs on the client.
 * 将四种 BEAR 物品排除出 Wathe 死亡掉落流程，无论任何 {@code ShouldDropOnDeath} 监听器如何回答（沿用 USEC 先例）；死者身上的
 * 这些物品随后由 BEAR 的 {@code KillPlayer.AFTER} 清理删除，而不会掉落在地。仅服务端：客户端从不执行 {@code killPlayer}。
 */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsBearItemDropMixin {
    @Inject(method = "shouldDropOnDeath", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$excludeBearItems(
            ItemStack stack,
            PlayerEntity victim,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (BearLoadoutService.blocksDeathDrop(stack)) {
            cir.setReturnValue(false);
        }
    }
}
