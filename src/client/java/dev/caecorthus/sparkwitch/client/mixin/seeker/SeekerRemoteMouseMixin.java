package dev.caecorthus.sparkwitch.client.mixin.seeker;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerRemoteViewClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While possessing, mouse look turns the remote focus (car heading and view pitch, or the camera cone) instead of the
 * body, which therefore never turns. The single {@code changeLookDirection(DD)V} call in {@code updateMouse} is
 * wrapped, not redirected, so it chains with the Control Expert stun's {@code @WrapWithCondition}. The hotbar scroll
 * bypasses KeyBinding and is blocked in game only; screens still receive it. First statement: the isActive() gate.
 * 附身期间，鼠标视角转动的是遥控焦点（车头与画面俯仰，或摄像头锥角），而不是本体，因此本体从不转向。
 * {@code updateMouse} 中唯一一处 {@code changeLookDirection(DD)V} 调用采用包装而非重定向，可与控场专家眩晕的
 * {@code @WrapWithCondition} 串联。快捷栏滚轮绕过 KeyBinding，仅在游戏内阻止，界面仍能收到。第一条语句为 isActive() 门槛。
 */
@Mixin(Mouse.class)
public abstract class SeekerRemoteMouseMixin {
    @WrapOperation(
            method = "updateMouse",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V")
    )
    private void sparkwitch$lookThroughFocus(ClientPlayerEntity player, double cursorDeltaX, double cursorDeltaY,
                                            Operation<Void> original) {
        if (!SeekerRemoteViewClient.isActive()) {
            original.call(player, cursorDeltaX, cursorDeltaY);
            return;
        }
        if (!SeekerRemoteViewClient.onLook(cursorDeltaX, cursorDeltaY)) {
            original.call(player, cursorDeltaX, cursorDeltaY);
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$blockScrollWhileViewing(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (!SeekerRemoteViewClient.isActive()) {
            return;
        }
        if (MinecraftClient.getInstance().currentScreen == null) {
            ci.cancel();
        }
    }
}
