package dev.caecorthus.sparkwitch.client.seeker.render;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCameraEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;

/**
 * Renderer for the placed camera model.
 * TODO(WP-03): render the Blockbench model. / 待 WP-03 渲染 Blockbench 模型。
 * 放置的摄像头模型渲染器。
 */
public class SeekerCameraEntityRenderer extends EntityRenderer<SeekerCameraEntity> {
    private static final Identifier TEXTURE = SparkWitch.id("textures/item/seeker_camera_placed.png");

    public SeekerCameraEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(SeekerCameraEntity entity) {
        return TEXTURE;
    }
}
