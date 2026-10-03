package dev.caecorthus.sparkwitch.client.blind.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.client.blind.BlindView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;

/**
 * Keeps short fog from culling the Blind's captured depth. Sodium's fog occlusion (on by default) searches chunk
 * sections only up to {@code min(renderDistance, RenderSystem.getShaderFogEnd() + 0.5)}, read inside
 * {@code WorldRenderer#setupTerrain}, and skips the entities and block entities of every section it drops. Blindness
 * (Wathe blackout, Last Stand), Darkness or Wathe's foggy-train fog would therefore remove most of the world from the
 * depth the echo pass reads. Right before {@code setupTerrain}, while the view is active, the terrain fog end is lifted
 * to at least the render distance; the colour this changes is discarded by the echo pass anyway.
 * 防止短雾剔除盲人捕获的深度。Sodium 的雾遮挡（默认开启）在 {@code WorldRenderer#setupTerrain} 内读取
 * {@code RenderSystem.getShaderFogEnd()}，只搜索 {@code min(渲染距离, 雾终点 + 0.5)} 以内的区块段，并跳过被剔除区块段中的
 * 实体与方块实体。失明（Wathe 停电、最后一搏）、黑暗或 Wathe 列车浓雾因此会把大部分世界从回声 pass 读取的深度中去掉。
 * 视图生效时，在 {@code setupTerrain} 之前把地形雾终点抬高到至少渲染距离；由此改变的颜色本来就会被回声 pass 丢弃。
 */
public final class BlindTerrainFog {
    private BlindTerrainFog() {
    }

    /** {@code WorldRenderer#render}, right before {@code setupTerrain}; render thread. / 渲染线程。 */
    public static void beforeTerrainSetup(GameRenderer gameRenderer) {
        if (!BlindView.isActive(MinecraftClient.getInstance())) {
            return;
        }
        float current = RenderSystem.getShaderFogEnd();
        float lifted = liftedFogEnd(current, gameRenderer.getViewDistance());
        if (lifted != current) {
            RenderSystem.setShaderFogEnd(lifted);
        }
    }

    /**
     * At least the render distance in blocks ({@code GameRenderer#getViewDistance}, the same value Sodium uses); never
     * lowers a longer fog and never forwards NaN (Sodium would cull everything and rebuild every frame).
     * 至少为以方块计的渲染距离（{@code GameRenderer#getViewDistance}，与 Sodium 使用的值相同）；不会降低更远的雾，也不会传出
     * NaN（否则 Sodium 会剔除一切并每帧重建）。
     */
    static float liftedFogEnd(float currentEnd, float viewDistance) {
        float floor = Float.isFinite(viewDistance) && viewDistance > 0.0f ? viewDistance : Float.MAX_VALUE;
        return Float.isNaN(currentEnd) ? floor : Math.max(currentEnd, floor);
    }
}
