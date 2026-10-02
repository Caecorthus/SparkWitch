package dev.caecorthus.sparkwitch.client.mixin.riftwalker;

import dev.caecorthus.sparkwitch.client.riftwalker.session.RiftSessionClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In-game scroll inside a gate bypasses KeyBinding, and for a spectator vanilla would cycle the spectator menu or change
 * the fly speed: HEAD cancels it (only in game, for our window, with no screen or overlay — screens still receive it)
 * and routes it to the ⬅️/➡️ selection. Mouse look is untouched (free look, plan §6.3). First statement: the
 * {@code isActive()} gate.
 * 门内的游戏内滚轮绕过 KeyBinding，而原版对旁观者会切换旁观者菜单或调整飞行速度：在 HEAD 取消（仅限游戏内、本窗口、没有界面或覆盖层时，
 * 界面仍能收到滚轮），并转为 ⬅️/➡️ 选择。鼠标视角不受影响（自由转头，plan §6.3）。第一条语句为 {@code isActive()} 门槛。
 */
@Mixin(Mouse.class)
public abstract class RiftSessionMouseMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$scrollRiftArrows(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (!RiftSessionClient.isActive()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (window != client.getWindow().getHandle() || client.currentScreen != null || client.getOverlay() != null) {
            return;
        }
        ci.cancel();
        RiftSessionClient.onScroll(client, horizontal, vertical);
    }
}
