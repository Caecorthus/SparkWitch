package dev.caecorthus.sparkwitch.client.mixin.blind;

import dev.caecorthus.sparkwitch.client.blind.render.BlindTerrainFog;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lifts the terrain fog end right before the single {@code setupTerrain} call in {@code WorldRenderer#render}, after
 * vanilla's (or Wathe's wrapped) {@code BackgroundRenderer#applyFog(FOG_TERRAIN)}, so Sodium's fog occlusion never
 * culls sections, entities or block entities out of the Blind's captured depth. Targets the call site, which Sodium's
 * {@code setupTerrain} overwrite leaves in place; a no-op unless the Blind view is active.
 * 在 {@code WorldRenderer#render} 中唯一一次 {@code setupTerrain} 调用之前（原版或 Wathe 包装后的
 * {@code BackgroundRenderer#applyFog(FOG_TERRAIN)} 之后）抬高地形雾终点，使 Sodium 的雾遮挡不会把区块段、实体或方块实体
 * 从盲人捕获的深度中剔除。注入点是调用处，Sodium 覆写 {@code setupTerrain} 方法体不影响它；盲人视图未生效时不做任何事。
 */
@Mixin(WorldRenderer.class)
public abstract class BlindTerrainFogMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;"
            + "setupTerrain(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;ZZ)V"))
    private void sparkwitch$liftBlindTerrainFog(RenderTickCounter tickCounter, boolean renderBlockOutline,
                                               Camera camera, GameRenderer gameRenderer,
                                               LightmapTextureManager lightmapTextureManager, Matrix4f positionMatrix,
                                               Matrix4f projectionMatrix, CallbackInfo ci) {
        BlindTerrainFog.beforeTerrainSetup(gameRenderer);
    }
}
