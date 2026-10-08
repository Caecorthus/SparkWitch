package dev.caecorthus.sparkwitch.client.mixin.scope;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.scope.ScopeClient;
import dev.caecorthus.sparkwitch.client.scope.ScopeHud;
import dev.caecorthus.sparkwitch.client.scope.ScopeProfile;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * While a scope profile is active, the crosshair layer draws the scope (HUD fallback ring, lens rim, the weapon's
 * reticle) instead of the vanilla or Wathe crosshair; otherwise it calls through unchanged. Priority 1100 keeps this
 * {@code @WrapMethod} outside Wathe's default-priority wrapper, which never calls the original during a game (Seeker
 * CCTV pattern). F1 already skips the layer; {@code ScopeHud} checks {@code hudHidden} too.
 * 开镜配置生效时，准星层绘制瞄准镜（HUD 回退环、镜框、武器自己的分划）而非原版或 Wathe 准星；否则原样调用。
 * 优先级 1100 使该 {@code @WrapMethod} 位于 Wathe 默认优先级包装器之外（后者在对局中从不调用原方法；参照搜寻者
 * CCTV）。按 F1 时该层本就不绘制；{@code ScopeHud} 也会检查 {@code hudHidden}。
 */
@Mixin(value = InGameHud.class, priority = 1100)
public abstract class ScopeInGameHudMixin {
    @WrapMethod(method = "renderCrosshair")
    private void sparkwitch$renderScopeCrosshair(DrawContext context, RenderTickCounter tickCounter,
                                                 Operation<Void> original) {
        ScopeProfile profile = ScopeClient.activeProfile();
        if (profile == null) {
            original.call(context, tickCounter);
            return;
        }
        ScopeHud.render(context, tickCounter, profile);
    }
}
