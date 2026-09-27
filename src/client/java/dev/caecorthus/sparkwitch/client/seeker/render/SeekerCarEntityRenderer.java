package dev.caecorthus.sparkwitch.client.seeker.render;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCarEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;

/**
 * Renderer for the placed car model.
 * TODO(WP-03): render the Blockbench model. / 待 WP-03 渲染 Blockbench 模型。
 * 放置的小车模型渲染器。
 */
public class SeekerCarEntityRenderer extends EntityRenderer<SeekerCarEntity> {
    private static final Identifier TEXTURE = SparkWitch.id("textures/item/seeker_car_placed.png");

    public SeekerCarEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(SeekerCarEntity entity) {
        return TEXTURE;
    }
}
