package dev.caecorthus.sparkwitch.client.specter;

import dev.caecorthus.sparkwitch.roles.killer.specter.GhostTriggerEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;

/**
 * The placed ghost trigger renders nothing: only its poison-style particles reveal the spot, and only to viewers who
 * can see poison. The entity still needs a registered renderer so it can be tracked and ticked client-side.
 * 已放置的鬼魂触发器不渲染任何东西：只有毒药粒子会暴露位置，且仅对能看见下毒的人可见。
 * 实体仍需要注册渲染器，才能被客户端追踪和 tick。
 */
public final class GhostTriggerEntityRenderer extends EntityRenderer<GhostTriggerEntity> {
    public GhostTriggerEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(
            GhostTriggerEntity entity,
            float yaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light
    ) {
    }

    @Override
    public boolean shouldRender(GhostTriggerEntity entity, Frustum frustum, double x, double y, double z) {
        return false;
    }

    @Override
    public Identifier getTexture(GhostTriggerEntity entity) {
        return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
    }
}
