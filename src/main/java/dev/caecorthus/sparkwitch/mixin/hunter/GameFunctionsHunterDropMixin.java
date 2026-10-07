package dev.caecorthus.sparkwitch.mixin.hunter;

import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterInventoryRules;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Excludes the Hunter loadout (shotgun, shells, traps) before Wathe's death-drop loop; the victim's copies are then
 * removed by the {@code KillPlayer.AFTER} cleanup instead of landing on the floor.
 * 在 Wathe 死亡掉落流程前排除猎人装备（猎枪、弹药、捕兽夹）；死者身上的装备随后由 {@code KillPlayer.AFTER} 清理移除，
 * 而不会掉落在地。
 */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsHunterDropMixin {
    @Inject(method = "shouldDropOnDeath", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$excludeHunterLoadout(
            ItemStack stack,
            PlayerEntity victim,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (HunterInventoryRules.isHunterLoadout(stack)) {
            cir.setReturnValue(false);
        }
    }
}
