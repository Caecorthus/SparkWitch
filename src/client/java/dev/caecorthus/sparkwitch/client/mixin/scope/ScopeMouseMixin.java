package dev.caecorthus.sparkwitch.client.mixin.scope;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.scope.ScopeRuntime;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Scoped mouse sensitivity: the single {@code changeLookDirection(DD)V} call in {@code updateMouse} is wrapped, not
 * redirected, and always calls through (scale 1 when unscoped), so the Seeker and Control Expert wrappers on the same
 * call keep applying. Client-only.
 * 开镜鼠标灵敏度：包装（而非重定向）{@code updateMouse} 中唯一一处 {@code changeLookDirection(DD)V} 调用，并始终继续调用
 * （未开镜时缩放为 1），因此同一调用上的搜寻者与控场专家包装依然生效。纯客户端。
 */
@Mixin(Mouse.class)
public abstract class ScopeMouseMixin {
    @WrapOperation(
            method = "updateMouse",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V")
    )
    private void sparkwitch$scaleScopeLook(ClientPlayerEntity player, double cursorDeltaX, double cursorDeltaY,
                                           Operation<Void> original) {
        double scale = ScopeRuntime.lookScale();
        original.call(player, cursorDeltaX * scale, cursorDeltaY * scale);
    }
}
