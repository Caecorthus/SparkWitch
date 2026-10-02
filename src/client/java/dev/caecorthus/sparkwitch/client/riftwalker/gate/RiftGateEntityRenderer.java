package dev.caecorthus.sparkwitch.client.riftwalker.gate;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;

/**
 * Renderer for the placed Rift Gate (model B {@code sparkwitch:item/rift_gate_placed}, drawn full-bright through
 * {@code ItemRenderer} FIXED like the Hunter trap, rotated to {@link RiftGateEntity#facing()}, lifted 0.75; see
 * {@code art/README.md} §"Moving it into src"). Visible to everyone. Registered by {@code RiftwalkerClient}. Owned by P6.
 * 已放置裂隙门的渲染器（模型 B {@code sparkwitch:item/rift_gate_placed}，与猎人捕兽夹一样经 {@code ItemRenderer} FIXED
 * 全亮度绘制，按 {@link RiftGateEntity#facing()} 旋转、上移 0.75；见 {@code art/README.md}）。所有人可见。
 * 由 {@code RiftwalkerClient} 注册。归属 P6。
 */
public final class RiftGateEntityRenderer extends EntityRenderer<RiftGateEntity> {
    public RiftGateEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(RiftGateEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        // TODO(P6): draw the placed model. G0 stub: draws nothing. / TODO(P6)：绘制放置模型。G0 存根：不绘制。
    }

    @Override
    public Identifier getTexture(RiftGateEntity entity) {
        return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
    }
}
