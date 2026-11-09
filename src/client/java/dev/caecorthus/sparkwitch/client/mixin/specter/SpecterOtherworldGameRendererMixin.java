package dev.caecorthus.sparkwitch.client.mixin.specter;

import dev.caecorthus.sparkwitch.client.specter.SpecterOtherworldScreenEffects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-only render hook: runs the Otherworld screen filter right before {@code GameRenderer#render}'s single
 * {@code Framebuffer#beginWrite(Z)} (after world, outlines and the vanilla post pass, before the GUI; the Black Raven /
 * Rift / Seeker / scope point). Vanilla reaches that call only on frames that render a world, so this hook never sees a
 * null world; releasing the pipeline is left to {@code SpecterOtherworldScreenEffects}' connection, stop and reload
 * hooks. The filter itself yields to every known shader.
 * 纯客户端渲染钩子：在 {@code GameRenderer#render} 唯一一次 {@code Framebuffer#beginWrite(Z)} 之前（世界、描边和原版
 * 后处理之后、GUI 之前，与黑羽鸦/裂隙/搜寻者/开镜同一位置）运行里世界滤镜。原版只在渲染世界的帧才会执行该调用，
 * 因此这里不会遇到空世界；管线释放交由 {@code SpecterOtherworldScreenEffects} 的连接、关闭与重载钩子负责。
 * 滤镜自身会对所有已知着色器让路。
 */
@Mixin(GameRenderer.class)
public abstract class SpecterOtherworldGameRendererMixin {
    @Shadow
    @Final
    MinecraftClient client;

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gl/Framebuffer;beginWrite(Z)V"))
    private void sparkwitch$renderOtherworldFilter(
            RenderTickCounter tickCounter,
            boolean tick,
            CallbackInfo ci
    ) {
        ClientPlayerEntity player = client.player;
        if (!tick || player == null) {
            return;
        }
        SpecterOtherworldScreenEffects.render(player, tickCounter.getLastFrameDuration());
    }
}
