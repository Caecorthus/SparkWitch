package dev.caecorthus.sparkwitch.client.mixin.potiongunner;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Read-only access to the vertical FOV the world is projected with this frame (options FOV x the eased FOV multiplier,
 * plus death and submersion changes and any other mod's {@code getFov} hooks), so the scope's range ticks match the
 * real projection. Calling it has no side effects.
 * 只读访问本帧世界投影所用的竖直视场角（选项 FOV x 缓动后的 FOV 倍率，加上死亡与水下修正及其他模组的
 * {@code getFov} 钩子），使瞄准镜射程刻度与真实投影一致。调用它没有副作用。
 */
@Mixin(GameRenderer.class)
public interface PotionScopeGameRendererInvoker {
    @Invoker("getFov")
    double sparkwitch$getFov(Camera camera, float tickDelta, boolean changingFov);
}
