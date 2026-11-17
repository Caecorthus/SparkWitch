package dev.caecorthus.sparkwitch.mixin.sphinxhao;

import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.gun.SphinxHaoGunInventory;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the 「“手”枪」 and its fist ammo out of Wathe's death-drop loop, whatever any {@code ShouldDropOnDeath} listener
 * answers (BEAR / USEC precedent); the victim's copies are then deleted by the {@code KillPlayer.AFTER} cleanup
 * ({@code SphinxHaoGunFeatureService}). Server only: {@code killPlayer} never runs on the client.
 * 将「“手”枪」与拳头子弹排除出 Wathe 死亡掉落流程，无论任何 {@code ShouldDropOnDeath} 监听器如何回答（沿用 BEAR / USEC 先例）；
 * 死者身上的副本随后由 {@code KillPlayer.AFTER} 清理删除（{@code SphinxHaoGunFeatureService}）。仅服务端。
 */
@Mixin(GameFunctions.class)
public abstract class GameFunctionsSphinxHaoGunDropMixin {
    @Inject(method = "shouldDropOnDeath", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$excludeSphinxHaoKit(
            ItemStack stack,
            PlayerEntity victim,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (SphinxHaoGunInventory.blocksDeathDrop(stack)) {
            cir.setReturnValue(false);
        }
    }
}
