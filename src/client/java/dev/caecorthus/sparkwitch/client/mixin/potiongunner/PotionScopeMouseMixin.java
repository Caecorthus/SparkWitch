package dev.caecorthus.sparkwitch.client.mixin.potiongunner;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.potiongunner.PotionScopeClient;
import dev.caecorthus.sparkwitch.client.potiongunner.PotionScopeRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Scoped mouse sensitivity: the single {@code changeLookDirection(DD)V} call in {@code updateMouse} is wrapped, not
 * redirected, and always calls through, so the Seeker remote-view wrapper and the Control Expert stun condition on the
 * same call keep applying. A Seeker session moves the camera off the player, which already ends the scoped state.
 * 开镜鼠标灵敏度：包装（而非重定向）{@code updateMouse} 中唯一一处 {@code changeLookDirection(DD)V} 调用，并始终继续
 * 调用，因此同一调用上的搜寻者遥控包装与控场专家眩晕条件依然生效。搜寻者会话会让相机离开玩家，开镜状态随之结束。
 */
@Mixin(Mouse.class)
public abstract class PotionScopeMouseMixin {
    @WrapOperation(
            method = "updateMouse",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V")
    )
    private void sparkwitch$scaleScopedLook(ClientPlayerEntity player, double cursorDeltaX, double cursorDeltaY,
                                            Operation<Void> original) {
        if (PotionScopeClient.isScoped(MinecraftClient.getInstance())) {
            original.call(player, cursorDeltaX * PotionScopeRules.SENSITIVITY_SCALE,
                    cursorDeltaY * PotionScopeRules.SENSITIVITY_SCALE);
            return;
        }
        original.call(player, cursorDeltaX, cursorDeltaY);
    }
}
