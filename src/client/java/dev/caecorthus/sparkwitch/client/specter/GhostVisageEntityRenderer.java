package dev.caecorthus.sparkwitch.client.specter;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.killer.specter.GhostVisageEntity;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * Renders the visage as a slim player model with the provided skin, standing where the entity stands. Only the
 * triggering player's client receives and renders it; opacity starts at 30% and fades to 0 over the entity's two-second
 * lifetime. Full-bright so the scare reads in dark rooms.
 * 幻影用细手臂玩家模型与提供的皮肤渲染，站在实体所在位置。只有触发者的客户端会收到并渲染；不透明度从 30% 在实体两秒
 * 生命周期内降到 0。使用全亮度，保证黑暗房间里也看得见。
 */
public final class GhostVisageEntityRenderer extends EntityRenderer<GhostVisageEntity> {
    private static final Identifier TEXTURE = SparkWitch.id("textures/entity/ghost_visage.png");
    private static final float START_ALPHA = 0.30F;
    private final PlayerEntityModel<GhostVisageEntity> model;

    public GhostVisageEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.model = new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER_SLIM), true);
        this.model.child = false;
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(
            GhostVisageEntity entity,
            float yaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light
    ) {
        float progress = Math.min(1.0F, (entity.age + tickDelta) / GhostVisageEntity.LIFETIME_TICKS);
        float alpha = START_ALPHA * (1.0F - progress);
        if (alpha <= 0.0F) {
            return;
        }
        matrices.push();
        // Copy LivingEntityRenderer's transforms in the same order: its setupTransforms uses 180 - yaw (not -yaw),
        // then the biped flip, then the -1.501 offset that puts the model's feet on the entity position.
        // 按 LivingEntityRenderer 的顺序补齐：setupTransforms 用 180° - yaw（不是 -yaw），再做人形翻转，
        // 最后 -1.501 让模型脚底落在实体坐标上。
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - yaw));
        matrices.scale(-1.0F, -1.0F, 1.0F);
        matrices.translate(0.0F, -1.501F, 0.0F);
        this.model.setAngles(entity, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        int color = ((int) (alpha * 255.0F) << 24) | 0xFFFFFF;
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
        this.model.render(
                matrices,
                consumer,
                LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV,
                color
        );
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public boolean shouldRender(GhostVisageEntity entity, Frustum frustum, double x, double y, double z) {
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null) {
            return false;
        }
        UUID viewerId = entity.getViewerUuid();
        return viewer.getUuid().equals(viewerId) && super.shouldRender(entity, frustum, x, y, z);
    }

    @Override
    public Identifier getTexture(GhostVisageEntity entity) {
        return TEXTURE;
    }
}
