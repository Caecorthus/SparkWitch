package dev.caecorthus.sparkwitch.client.mixin.scope;

import dev.caecorthus.sparkwitch.client.scope.ScopeRuntime;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-only render hook of the reusable scope module: {@code render} before the single
 * {@code Framebuffer#beginWrite(Z)} (after the world, the hand and the in-wall/underwater/fire overlays, the entity
 * outline composite and the vanilla post pass, before the GUI; the Seeker filter's point) runs the per-frame scope
 * update, the PiP lens render and the lens composite, so the HUD and the reticle are never blurred. The held item is
 * hidden by {@code ScopeHeldItemRendererMixin}; {@code renderHand} itself always runs, so its overlays still draw.
 * 可复用开镜模块的纯客户端渲染钩子：{@code render} 中唯一一次 {@code Framebuffer#beginWrite(Z)} 之前（世界、手与墙内/水下/
 * 着火覆盖层、实体描边合成及原版后处理之后，GUI 之前，与搜寻者滤镜同一位置）运行逐帧开镜更新、画中画镜内渲染与镜片合成，
 * 因此 HUD 与分划永远不会被模糊。手持物品由 {@code ScopeHeldItemRendererMixin} 隐藏；{@code renderHand} 本身始终执行，
 * 其覆盖层照常绘制。
 */
@Mixin(GameRenderer.class)
public abstract class ScopeGameRendererMixin {
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gl/Framebuffer;beginWrite(Z)V"))
    private void sparkwitch$renderScopeLens(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        ScopeRuntime.beforeGui(tickCounter);
    }
}
