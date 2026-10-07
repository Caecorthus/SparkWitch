package dev.caecorthus.sparkwitch.mixin.abysslistener;

import dev.caecorthus.sparkwitch.roles.witch.abysslistener.loadout.AbyssListenerInventoryRules;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Defensively excludes the Shriek Gun from Wathe's death-drop loop (Wathe drops only revolvers by default, but
 * {@code ShouldDropOnDeath} listeners may add more); the victim's gun is then removed by the {@code KillPlayer.AFTER}
 * cleanup instead of landing on the floor.
 * 防御性地将啸音铳排除出 Wathe 死亡掉落流程（Wathe 默认只掉落左轮，但 {@code ShouldDropOnDeath} 监听器可能追加）；
 * 死者身上的枪随后由 {@code KillPlayer.AFTER} 清理移除，而不会掉落在地。
 */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsAbyssListenerGunDropMixin {
    @Inject(method = "shouldDropOnDeath", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$excludeShriekGun(
            ItemStack stack,
            PlayerEntity victim,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (AbyssListenerInventoryRules.blocksDeathDrop(stack)) {
            cir.setReturnValue(false);
        }
    }
}
