package dev.caecorthus.sparkwitch.client.mixin.blackraven;

import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenPerceptionScreenEffects;
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

/** Runs Perception desaturation after world rendering and before the HUD. / 在世界渲染后、HUD 前运行感知去饱和。 */
@Mixin(GameRenderer.class)
public abstract class BlackRavenGameRendererMixin {
    @Shadow
    @Final
    MinecraftClient client;

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;beginWrite(Z)V"))
    private void sparkwitch$renderBlackRavenPerception(
            RenderTickCounter tickCounter,
            boolean tick,
            CallbackInfo ci
    ) {
        ClientPlayerEntity player = client.player;
        if (!tick || client.world == null || player == null) {
            return;
        }
        BlackRavenPerceptionScreenEffects.render(player, tickCounter.getTickDelta(true));
    }
}
