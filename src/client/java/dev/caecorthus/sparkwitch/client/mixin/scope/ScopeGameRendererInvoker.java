package dev.caecorthus.sparkwitch.client.mixin.scope;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Read-only access to the vertical FOV the world is projected with this frame (options FOV x the eased zoom, plus
 * death, submersion and any other mod's {@code getFov} hooks), handed to reticles as
 * {@code ScopeFrame#projectionFovDegrees}. Calling it has no side effects.
 * 只读访问本帧世界投影所用的竖直视场角（选项 FOV x 缓动后的放大，再加死亡、水下及其他模组的 {@code getFov} 钩子），
 * 作为 {@code ScopeFrame#projectionFovDegrees} 交给分划。调用它没有副作用。
 */
@Mixin(GameRenderer.class)
public interface ScopeGameRendererInvoker {
    @Invoker("getFov")
    double sparkwitch$scopeFov(Camera camera, float tickDelta, boolean changingFov);
}
