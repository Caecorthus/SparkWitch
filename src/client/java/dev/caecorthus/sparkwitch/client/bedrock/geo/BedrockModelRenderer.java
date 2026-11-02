package dev.caecorthus.sparkwitch.client.bedrock.geo;

import dev.caecorthus.sparkwitch.client.bedrock.BedrockBounds;
import dev.caecorthus.sparkwitch.client.bedrock.BedrockPose;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.jetbrains.annotations.Nullable;

/**
 * Draws a {@link BedrockGeometry} posed by a {@link BedrockPose}. Render frame contract: y up, the model faces -z
 * (Bedrock north), 1 unit = 1 block, and a Bedrock point (x, y, z) px of an unrotated root bone lands at
 * (-x / 16, y / 16, z / 16) — GeckoLib's convention, equivalent to vanilla {@code ModelPart} space
 * (x, 24 - y, z) px with (pitch, yaw, roll) = Bedrock (rx, ry, rz) under the entity renderer's scale(-1, -1, 1).
 * 绘制由 {@link BedrockPose} 摆姿的 {@link BedrockGeometry}。渲染坐标系约定：y 轴向上，模型朝向 -z（Bedrock 北），1 单位
 * = 1 格，未旋转根骨骼上的 Bedrock 点 (x, y, z) 像素落在 (-x / 16, y / 16, z / 16)——即 GeckoLib 的约定，等价于原版
 * {@code ModelPart} 空间 (x, 24 - y, z) 像素、(pitch, yaw, roll) = Bedrock (rx, ry, rz)，再经实体渲染器的
 * scale(-1, -1, 1)。
 */
public final class BedrockModelRenderer {
    private BedrockModelRenderer() {
    }

    /**
     * Emits every visible face as entity-format quads; glow bones use full-bright light instead of {@code light}.
     * 以实体顶点格式输出所有可见面；发光骨骼使用全亮光照而非 {@code light}。
     */
    public static void render(BedrockGeometry geometry, BedrockPose pose, MatrixStack matrices,
                              VertexConsumer consumer, int light, int overlay, int argb) {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * The box around every cube corner in the render frame; null pose = bind pose. {@link BedrockBounds#EMPTY} for a
     * model without cubes.
     * 渲染坐标系中包住所有方块角点的包围盒；pose 为 null 时取绑定姿态。没有方块的模型返回 {@link BedrockBounds#EMPTY}。
     */
    public static BedrockBounds bounds(BedrockGeometry geometry, @Nullable BedrockPose pose) {
        throw new UnsupportedOperationException("TODO");
    }
}
