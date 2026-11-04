package dev.caecorthus.sparkwitch.client.mixin.dominator;

import dev.caecorthus.sparkwitch.client.dominator.DominatorForcedShotClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks every open path of the inventory and of container screens (including Wathe's limited screen) while the
 * forced-shot lock is active; a refused container screen is closed on the server as well.
 * 强制开枪锁生效期间拦截打开物品栏与容器界面的所有路径（包括 Wathe 的受限界面）；被拒绝的容器界面也会在服务端关闭。
 */
@Mixin(MinecraftClient.class)
public abstract class DominatorForcedShotInventoryMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$blockForcedShotInventory(Screen screen, CallbackInfo ci) {
        if (DominatorForcedShotClientState.blocks(screen)) {
            DominatorForcedShotClientState.refuse(screen);
            ci.cancel();
        }
    }
}
