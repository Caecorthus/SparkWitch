package dev.caecorthus.sparkwitch.client.mixin.scope;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.caecorthus.sparkwitch.client.scope.ScopeRuntime;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Scope zoom for the reusable scope module: multiplies every return of {@code getFovMultiplier} by the active
 * profile's zoom, for the local player only. It chains with Wathe's RETURN hook and the Potion Gunner's own zoom (never
 * replaces them), and {@code GameRenderer} eases the multiplier in and out like the spyglass. Client-only presentation.
 * 可复用开镜模块的放大：仅对本地玩家，把 {@code getFovMultiplier} 的每个返回值乘以当前配置的放大倍率。与 Wathe 的
 * RETURN 钩子及药炮手自己的放大串联（从不替换），{@code GameRenderer} 会像望远镜一样平滑过渡。纯客户端展示。
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class ScopeFovMixin {
    @ModifyReturnValue(method = "getFovMultiplier", at = @At("RETURN"))
    private float sparkwitch$scopeZoom(float original) {
        return ScopeRuntime.fovMultiplier((AbstractClientPlayerEntity) (Object) this, original);
    }
}
