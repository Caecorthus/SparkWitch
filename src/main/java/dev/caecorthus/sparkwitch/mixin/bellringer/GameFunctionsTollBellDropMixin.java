package dev.caecorthus.sparkwitch.mixin.bellringer;

import dev.caecorthus.sparkwitch.roles.killer.bellringer.BellRingerInventoryRules;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Excludes the bound bell before Wathe's death-drop loop; the victim's copy is then removed by the
 * {@code KillPlayer.AFTER} cleanup instead of landing on the floor.
 * 在 Wathe 死亡掉落流程前排除绑定之钟；死者身上的钟随后由 {@code KillPlayer.AFTER} 清理移除，而不会掉落在地。
 */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsTollBellDropMixin {
    @Inject(method = "shouldDropOnDeath", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$excludeTollBell(
            ItemStack stack,
            PlayerEntity victim,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (BellRingerInventoryRules.blocksDeathDrop(stack)) {
            cir.setReturnValue(false);
        }
    }
}
