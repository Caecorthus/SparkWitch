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
 * Runs the Otherworld screen filter right before the GUI binds the main framebuffer (after world, outlines and the
 * vanilla post pass; the Black Raven / Rift / Seeker point). The filter itself yields to every known shader.
 * 在 GUI 绑定主帧缓冲之前（世界、描边和原版后处理之后，与黑羽鸦/裂隙/搜寻者同一位置）运行里世界滤镜。
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
        if (!tick) {
            return;
        }
        ClientPlayerEntity player = client.player;
        if (client.world == null || player == null) {
            SpecterOtherworldScreenEffects.close();
            return;
        }
        SpecterOtherworldScreenEffects.render(player, tickCounter.getLastFrameDuration());
    }
}
