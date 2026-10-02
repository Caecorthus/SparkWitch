package dev.caecorthus.sparkwitch.mixin.blind;

import dev.caecorthus.sparkwitch.roles.civilian.blind.kit.BlindInventoryRules;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Defensively excludes the White Cane and ComTac VIII from Wathe's death-drop loop (Wathe drops only revolvers plus
 * what {@code ShouldDropOnDeath} listeners allow); the victim's copies are then removed by the {@code KillPlayer.AFTER}
 * strip instead of landing on the floor.
 * 防御性地将盲杖与 ComTac VIII 排除出 Wathe 死亡掉落流程（Wathe 只掉落左轮及 {@code ShouldDropOnDeath} 监听器允许的
 * 物品）；死者身上的副本随后由 {@code KillPlayer.AFTER} 清理移除，而不会掉落在地。
 */
@Mixin(GameFunctions.class)
public abstract class BlindKitDeathDropMixin {
    @Inject(method = "shouldDropOnDeath", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$excludeBlindKit(
            ItemStack stack,
            PlayerEntity victim,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (BlindInventoryRules.blocksDeathDrop(stack)) {
            cir.setReturnValue(false);
        }
    }
}
