package dev.caecorthus.sparkwitch.client.mixin.wraith;

import dev.caecorthus.sparkwitch.client.wraith.WraithScreenEffects;
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

/** Renders Wraith grayscale after the world and before the HUD. / 在世界渲染后、HUD 前绘制冤魂灰阶。 */
@Mixin(value = GameRenderer.class, priority = 1200)
public abstract class WraithGameRendererMixin {
    @Shadow
    @Final
    MinecraftClient client;

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;beginWrite(Z)V"))
    private void sparkwitch$renderWraithScreenEffects(
            RenderTickCounter tickCounter,
            boolean tick,
            CallbackInfo ci
    ) {
        ClientPlayerEntity player = client.player;
        if (!tick) {
            return;
        }
        if (client.world == null || player == null) {
            WraithScreenEffects.close();
            return;
        }
        WraithScreenEffects.render(player, tickCounter.getTickDelta(true));
    }
}
