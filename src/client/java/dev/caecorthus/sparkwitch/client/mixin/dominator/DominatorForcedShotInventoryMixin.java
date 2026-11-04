package dev.caecorthus.sparkwitch.client.mixin.dominator;

import dev.caecorthus.sparkwitch.client.dominator.DominatorForcedShotClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks every open path of the inventory (including Wathe's limited screen) while the forced-shot lock is active.
 * 强制开枪锁生效期间拦截打开物品栏的所有路径（包括 Wathe 的受限界面）。
 */
@Mixin(MinecraftClient.class)
public abstract class DominatorForcedShotInventoryMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$blockForcedShotInventory(Screen screen, CallbackInfo ci) {
        if (DominatorForcedShotClientState.blocks(screen)) {
            ci.cancel();
        }
    }
}
