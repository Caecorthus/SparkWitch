package dev.caecorthus.sparkwitch.client.mixin.scope;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.client.scope.ScopeRuntime;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Scope zoom for the reusable scope module, applied to the value {@code GameRenderer#updateFovMultiplier} reads from
 * the single {@code AbstractClientPlayerEntity#getFovMultiplier()} call, before vanilla eases it (half the gap per tick)
 * and clamps it to [0.1, 1.5]. Hooking the call site, not the player method's RETURN, keeps the zoom independent of
 * mixin order: Wathe's cancellable RETURN {@code @Inject} ({@code wathe$fovPulse}) sets the return value and would
 * skip a {@code @ModifyReturnValue} applied after it, but every player-side hook (such as Wathe's pulse) has finished
 * by the time this value is read. Only the camera player's multiplier reaches this call, and the module's view gate
 * also requires the camera to be the local player. PICTURE_IN_PICTURE keeps the main view at 1x. Client-only
 * presentation.
 * 可复用开镜模块的放大：作用于 {@code GameRenderer#updateFovMultiplier} 从唯一一处 {@code AbstractClientPlayerEntity#getFovMultiplier()}
 * 调用读到的值，在原版缓动（每 tick 缩小一半差距）并钳制到 [0.1, 1.5] 之前。挂在调用处而非玩家方法的 RETURN，使放大与
 * mixin 顺序无关：Wathe 可取消的 RETURN {@code @Inject}（{@code wathe$fovPulse}）会设置返回值，并跳过在它之后应用的
 * {@code @ModifyReturnValue}；而读取该值时，所有玩家侧钩子（例如 Wathe 的脉冲）都已执行完毕。只有相机玩家的倍率会
 * 到达这里，开镜模块的视角条件也要求相机就是本地玩家。画中画模式下主画面保持 1 倍。纯客户端展示。
 */
@Mixin(GameRenderer.class)
public abstract class ScopeFovMixin {
    @ModifyExpressionValue(method = "updateFovMultiplier", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;getFovMultiplier()F"))
    private float sparkwitch$scopeZoom(float original) {
        return ScopeRuntime.fovMultiplier(original);
    }
}
