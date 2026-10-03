package dev.caecorthus.sparkwitch.mixin.potiongunner;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerInventoryRules;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Excludes the launcher and shells from Wathe's death-drop loop (Wathe drops only revolvers by default, but any
 * listener may opt in); the victim's copies are then removed by the {@code KillPlayer.AFTER} strip instead of landing
 * on the floor. Server-only: {@code killPlayer} never runs on the client.
 * 将炮筒与炮弹排除出 Wathe 死亡掉落流程（Wathe 默认只掉落左轮，但任何监听器都可加入）；死者身上的这些物品随后由
 * {@code KillPlayer.AFTER} 收走，而不会掉落在地。仅服务端：客户端从不执行 {@code killPlayer}。
 */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsPotionGunnerItemDropMixin {
    @Inject(method = "shouldDropOnDeath", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$excludePotionGunnerItems(
            ItemStack stack,
            PlayerEntity victim,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (PotionGunnerInventoryRules.blocksDeathDrop(stack)) {
            cir.setReturnValue(false);
        }
    }
}
