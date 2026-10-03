package dev.caecorthus.sparkwitch.mixin.saint;

import dev.caecorthus.sparkwitch.roles.civilian.saint.flash.HolyFlashInventoryRules;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Excludes Holy Flashes before Wathe's death-drop loop (owner decision: never dropped on death); the victim's copies
 * are then removed by the {@code KillPlayer.AFTER} cleanup in {@code HolyFlashFeatureService} instead of landing on
 * the floor.
 * 在 Wathe 死亡掉落流程前排除圣光弹（所有者决定：死亡不掉落）；死者身上的圣光弹随后由 {@code HolyFlashFeatureService}
 * 的 {@code KillPlayer.AFTER} 清理移除，而不会掉落在地。
 */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsHolyFlashDropMixin {
    @Inject(method = "shouldDropOnDeath", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$excludeHolyFlash(
            ItemStack stack,
            PlayerEntity victim,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (HolyFlashInventoryRules.blocksDeathDrop(stack)) {
            cir.setReturnValue(false);
        }
    }
}
