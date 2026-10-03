package dev.caecorthus.sparkwitch.client.mixin.blind;

import dev.caecorthus.sparkwitch.client.blind.render.BlindEchoView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs the Blind's end-of-frame echo pass right AFTER the single {@code Framebuffer#beginWrite(Z)} in
 * {@code GameRenderer#render}: after world, hand, outline composite, vanilla post and every SparkWitch filter that
 * injects before that call (Seeker, Black Raven), and before the GUI. {@code shift = AFTER} orders it after them
 * without mixin priorities, so it consumes their output. {@link BlindEchoView#renderFrame} is a no-op release when
 * the local player is not an active Blind, and re-binds the main framebuffer before the GUI draws.
 * 在 {@code GameRenderer#render} 唯一一次 {@code Framebuffer#beginWrite(Z)} 之后运行盲人的帧末回声 pass：位于世界、手、
 * 描边合成、原版后处理以及所有在该调用之前注入的 SparkWitch 滤镜（搜寻者、黑羽鸦）之后，GUI 之前。
 * {@code shift = AFTER} 无需 mixin 优先级即可排在它们之后并吞掉其输出。本地玩家不是激活的盲人时
 * {@link BlindEchoView#renderFrame} 只做释放；GUI 绘制前会重新绑定主帧缓冲。
 */
@Mixin(GameRenderer.class)
public abstract class BlindGameRendererMixin {
    @Shadow
    @Final
    MinecraftClient client;

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gl/Framebuffer;beginWrite(Z)V", shift = At.Shift.AFTER))
    private void sparkwitch$renderBlindEcho(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        BlindEchoView.renderFrame(client);
    }
}
