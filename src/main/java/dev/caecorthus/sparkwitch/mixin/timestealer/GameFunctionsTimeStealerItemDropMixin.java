package dev.caecorthus.sparkwitch.mixin.timestealer;

import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStealerInventoryRules;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Defensively excludes the Clock and Time Stamps from Wathe's death-drop loop (Wathe drops only revolvers by default);
 * the victim's copies are then removed by the {@code KillPlayer.AFTER} cleanup instead of landing on the floor.
 * 防御性地将时钟与时光邮票排除出 Wathe 死亡掉落流程（Wathe 默认只掉落左轮）；死者身上的这些物品随后由
 * {@code KillPlayer.AFTER} 清理移除，而不会掉落在地。
 */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsTimeStealerItemDropMixin {
    @Inject(method = "shouldDropOnDeath", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$excludeTimeStealerItems(
            ItemStack stack,
            PlayerEntity victim,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (TimeStealerInventoryRules.blocksDeathDrop(stack)) {
            cir.setReturnValue(false);
        }
    }
}
