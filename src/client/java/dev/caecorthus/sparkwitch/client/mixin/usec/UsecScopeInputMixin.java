package dev.caecorthus.sparkwitch.client.mixin.usec;

import dev.caecorthus.sparkwitch.client.usec.UsecScopeInput;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * USEC Shift + right-click (client presentation only). The only place the client starts using the rifle is
 * {@code doItemUse} inside {@code handleInputEvents}, and {@code MinecraftClient#tick} runs {@code GameRenderer#tick}
 * (whose {@code updateFovMultiplier} samples the scope zoom) right after it. Seeing the raise at {@code RETURN} lets
 * {@code UsecScopeInput.afterInputEvents} jump the magnification before that first sample, so a jump never shows one
 * tick of the previous magnification. Read-only: never cancels and changes no vanilla state; the end-of-tick scope
 * intent stays in {@code UsecScopeInput.tick}.
 * USEC 的 Shift + 右键（仅客户端展示）。客户端开始使用步枪的唯一位置是 {@code handleInputEvents} 中的 {@code doItemUse}，
 * 而 {@code MinecraftClient#tick} 紧接着运行 {@code GameRenderer#tick}（其中 {@code updateFovMultiplier} 采样开镜倍率）。
 * 在 {@code RETURN} 处看到举枪，{@code UsecScopeInput.afterInputEvents} 就能在第一次采样之前跳转倍率，跳转时不会先显示一刻
 * 原来的倍率。只读：从不取消，也不改变任何原版状态；刻末的开镜意图仍在 {@code UsecScopeInput.tick} 中。
 */
@Mixin(MinecraftClient.class)
public abstract class UsecScopeInputMixin {
    @Inject(method = "handleInputEvents", at = @At("RETURN"))
    private void sparkwitch$jumpUsecZoomOnRaise(CallbackInfo ci) {
        UsecScopeInput.afterInputEvents((MinecraftClient) (Object) this);
    }
}
