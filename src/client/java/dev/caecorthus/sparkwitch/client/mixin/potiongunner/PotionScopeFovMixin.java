package dev.caecorthus.sparkwitch.client.mixin.potiongunner;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.caecorthus.sparkwitch.client.potiongunner.PotionScopeClient;
import dev.caecorthus.sparkwitch.client.potiongunner.PotionScopeRules;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Scope zoom: multiplies every return of {@code getFovMultiplier} for the scoped local player only. It chains with
 * Wathe's poison-pulse RETURN hook (never replaces it), and {@code GameRenderer} eases the multiplier in and out over
 * a few ticks like the spyglass. Client-only presentation; the server never sees it.
 * 瞄准镜放大：仅对开镜中的本地玩家，将 {@code getFovMultiplier} 的每个返回值乘以缩放倍率。它与 Wathe 的中毒脉冲
 * RETURN 钩子串联（从不替换），{@code GameRenderer} 会像望远镜一样在几刻内平滑过渡。纯客户端展示，服务端不可见。
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class PotionScopeFovMixin {
    @ModifyReturnValue(method = "getFovMultiplier", at = @At("RETURN"))
    private float sparkwitch$zoomWhileScoped(float original) {
        if (!PotionScopeClient.isScopedLocalPlayer((AbstractClientPlayerEntity) (Object) this)) {
            return original;
        }
        return original * PotionScopeRules.ZOOM_FACTOR;
    }
}
