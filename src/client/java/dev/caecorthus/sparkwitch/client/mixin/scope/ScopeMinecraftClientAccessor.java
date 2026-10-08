package dev.caecorthus.sparkwitch.client.mixin.scope;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets the PiP lens pass point {@code MinecraftClient#getFramebuffer()} at its lens target for the length of one
 * {@code WorldRenderer#render} call, because vanilla re-binds {@code client.getFramebuffer()} inside that method (after
 * the entity-outline clear and post pass) and {@code RenderPhase} targets bind it to end their draws. Only
 * {@code ScopePictureInPicture} calls it, always restoring the main framebuffer in a {@code finally} on the render
 * thread before anything else reads it.
 * 让画中画镜内渲染在一次 {@code WorldRenderer#render} 调用期间把 {@code MinecraftClient#getFramebuffer()} 指向镜内目标：原版
 * 在该方法内（实体描边清除与后处理之后）会重新绑定 {@code client.getFramebuffer()}，{@code RenderPhase} 目标结束绘制时也会
 * 绑定它。仅 {@code ScopePictureInPicture} 调用，并总在渲染线程的 {@code finally} 中、任何其他代码读取之前恢复主帧缓冲。
 */
@Mixin(MinecraftClient.class)
public interface ScopeMinecraftClientAccessor {
    @Mutable
    @Accessor("framebuffer")
    void sparkwitch$setScopeFramebuffer(Framebuffer framebuffer);
}
