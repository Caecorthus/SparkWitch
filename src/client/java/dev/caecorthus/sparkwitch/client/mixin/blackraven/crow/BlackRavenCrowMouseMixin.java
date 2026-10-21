package dev.caecorthus.sparkwitch.client.mixin.blackraven.crow;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.blackraven.crow.BlackRavenCrowPilotClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While flying the crow, mouse look turns the crow instead of the body, which therefore never turns. The single
 * {@code changeLookDirection(DD)V} call in {@code updateMouse} is wrapped, not redirected, so it chains with the Seeker
 * view and the Control Expert stun on the same call. The hotbar scroll (which bypasses KeyBinding) is blocked in game
 * only. First statement: the {@code isActive()} gate.
 * 操控乌鸦期间，鼠标视角转动乌鸦而非本体，本体因此从不转向。updateMouse 中唯一的 changeLookDirection(DD)V 调用采用包装而非重定向，
 * 可与搜寻者视角及控场专家眩晕在同一调用上串联。快捷栏滚轮（绕过 KeyBinding）仅在游戏内被阻止。
 */
@Mixin(Mouse.class)
public abstract class BlackRavenCrowMouseMixin {
    @WrapOperation(
            method = "updateMouse",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V")
    )
    private void sparkwitch$lookThroughCrow(ClientPlayerEntity player, double cursorDeltaX, double cursorDeltaY,
                                           Operation<Void> original) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            original.call(player, cursorDeltaX, cursorDeltaY);
            return;
        }
        if (!BlackRavenCrowPilotClient.steer(cursorDeltaX, cursorDeltaY)) {
            original.call(player, cursorDeltaX, cursorDeltaY);
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$noHotbarScrollAsCrow(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return;
        }
        if (MinecraftClient.getInstance().currentScreen == null) {
            ci.cancel();
        }
    }
}
