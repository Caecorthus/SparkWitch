package dev.caecorthus.sparkwitch.client.riftwalker.gate;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * Renderer for the placed Rift Gate (model B {@code sparkwitch:item/rift_gate_placed}, drawn full-bright through
 * {@code ItemRenderer} FIXED like the Hunter trap, rotated to {@link RiftGateEntity#facing()}, lifted 0.75; see
 * {@code art/README.md} §"Moving it into src"). Visible to everyone: it never overrides {@code shouldRender}, so the
 * entity's own 64-block distance and visibility box are the only culling, and full-bright keeps it lit during a
 * blackout. The swirl is a zero-thickness element with north and south faces, so the culling item layer still shows it
 * from both sides; its atlas animation needs no code. The instinct outline (D9) reuses this draw: Wathe's outline pass
 * hands an outline provider in, and the item layer feeds the outline buffer. Registered by {@code RiftwalkerClient}.
 * 已放置裂隙门的渲染器（模型 B，经 {@code ItemRenderer} FIXED 全亮度绘制，按 {@link RiftGateEntity#facing()} 旋转、上移 0.75；
 * 见 {@code art/README.md}）。所有人可见：不重写 {@code shouldRender}，只受实体自身 64 格距离与可见包围盒剔除；全亮度保证停电时
 * 依旧可见。旋涡是带南北两面的零厚度元素，剔除背面的物品层仍能从两侧看到；其图集动画无需代码。本能描边（D9）复用这次绘制：
 * Wathe 的描边通道传入描边缓冲提供者，物品层会同时写入描边缓冲。由 {@code RiftwalkerClient} 注册。
 */
public final class RiftGateEntityRenderer extends EntityRenderer<RiftGateEntity> {
    private final ItemRenderer itemRenderer;
    private final ItemStack stack;

    public RiftGateEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
        stack = new ItemStack(SparkWitchItems.riftGate());
    }

    @Override
    public void render(RiftGateEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        // Lift model y −4 onto the base, then turn the north-facing front to FACING about the gate's vertical axis.
        // 先把模型 y −4 抬到门底，再绕门的竖直轴把朝北的正面转向 FACING。
        matrices.translate(0.0, RiftGatePresentationRules.MODEL_LIFT, 0.0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(RiftGatePresentationRules.yawDegrees(entity.facing())));
        itemRenderer.renderItem(stack, ModelTransformationMode.FIXED, false, matrices, vertexConsumers,
                LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, RiftGateModels.placedModel());
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(RiftGateEntity entity) {
        return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
    }
}
