package dev.caecorthus.sparkwitch.client.mixin.scope;

import dev.caecorthus.sparkwitch.client.scope.ScopeRuntime;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-only render hooks of the reusable scope module:
 * <ul>
 *   <li>{@code renderHand} HEAD: cancel while scoped (Potion Gunner / Seeker pattern; Iris can bypass
 *   {@code setRenderHand(false)}). Other players still see the held weapon.</li>
 *   <li>{@code render} before the single {@code Framebuffer#beginWrite(Z)} (after the world, outlines and the vanilla
 *   post pass, before the GUI; the Seeker filter's point): the per-frame scope update and the private lens filter, so
 *   the HUD and the reticle are never blurred.</li>
 * </ul>
 * 可复用开镜模块的纯客户端渲染钩子：{@code renderHand} 在 HEAD 于开镜时取消（参照药炮手 / 搜寻者；Iris 可能绕过
 * {@code setRenderHand(false)}），其他玩家仍能看到手持武器；{@code render} 在唯一一次 {@code Framebuffer#beginWrite(Z)}
 * 之前（世界、描边与原版后处理之后，GUI 之前，与搜寻者滤镜同一位置）运行逐帧开镜更新与私有镜片滤镜，因此 HUD 与分划
 * 永远不会被模糊。
 */
@Mixin(GameRenderer.class)
public abstract class ScopeGameRendererMixin {
    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$hideScopeHand(CallbackInfo ci) {
        if (ScopeRuntime.hidesHand()) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gl/Framebuffer;beginWrite(Z)V"))
    private void sparkwitch$renderScopeLens(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        ScopeRuntime.beforeGui(tickCounter);
    }
}
