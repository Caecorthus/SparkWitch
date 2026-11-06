package dev.caecorthus.sparkwitch.client.mixin.bear;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.client.bear.launcher.M32SightState;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The hand-pass FOV while the M32A1's M2A1 aim progress is above 0: {@code GameRenderer#getFov} with
 * {@code changingFov == false} is the hand projection's FOV (vanilla {@code renderHand} and Iris's
 * {@code HandRenderer.setupGlState} both ask it), and it becomes lerp(vanilla, 45, ease(p)) through
 * {@link M32SightState#handFov}, which returns the original value exactly at p = 0. The world projection
 * ({@code changingFov == true}, also read by the scope HUD's invoker) always passes through unchanged; its zoom comes
 * from the M32 scope profile. Client-only presentation.
 * M32A1 的 M2A1 瞄准进度大于 0 时的手部渲染视场角：{@code changingFov == false} 的 {@code GameRenderer#getFov} 即手部投影的视场角
 * （原版 {@code renderHand} 与 Iris 的 {@code HandRenderer.setupGlState} 都会询问），经 {@link M32SightState#handFov} 变为
 * lerp(原版, 45, ease(p))；p = 0 时严格返回原值。世界投影（{@code changingFov == true}，开镜 HUD 的 invoker 也会读取）总是原样
 * 通过，其放大来自 M32 开镜配置。纯客户端展示。
 */
@Mixin(GameRenderer.class)
public abstract class M32SightFovMixin {
    @ModifyReturnValue(method = "getFov(Lnet/minecraft/client/render/Camera;FZ)D", at = @At("RETURN"))
    private double sparkwitch$m32HandFov(double original, @Local(argsOnly = true) boolean changingFov) {
        return changingFov ? original : M32SightState.handFov(original);
    }
}
