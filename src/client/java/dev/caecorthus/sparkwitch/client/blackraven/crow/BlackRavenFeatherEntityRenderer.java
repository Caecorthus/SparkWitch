package dev.caecorthus.sparkwitch.client.blackraven.crow;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenFeatherEntity;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenFeatherRegistry;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Draws a dropped Homing Feather marker with the {@code sparkwitch:homing_feather} item sprite, like a dropped item
 * (ground transform) but turning and bobbing slowly, so it reads as a resting feather. Visible to every viewer (owner
 * decision Q7); presentation only, the server owns the marker.
 * 用 {@code sparkwitch:homing_feather} 物品图标绘制已丢出的归羽标记，类似掉落物（地面变换），但转动与浮动都更缓慢，看起来像一根静止的羽毛。
 * 对所有观看者可见（所有者决定 Q7）；仅为表现，标记由服务端持有。
 */
public final class BlackRavenFeatherEntityRenderer extends EntityRenderer<BlackRavenFeatherEntity> {
    private static final float BOB_HEIGHT = 0.05F;
    private static final float HOVER = 0.15F;
    private static final float BOB_PERIOD = 20.0F;
    private static final float TURN_PERIOD = 40.0F;

    private final ItemRenderer itemRenderer;
    private final ItemStack stack;

    public BlackRavenFeatherEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
        stack = new ItemStack(BlackRavenFeatherRegistry.homingFeather());
        shadowRadius = 0.1F;
        shadowOpacity = 0.5F;
    }

    @Override
    public void render(BlackRavenFeatherEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        float time = entity.age + tickDelta;
        float phase = entity.getId() * 0.37F;
        matrices.push();
        matrices.translate(0.0F, HOVER + MathHelper.sin(time / BOB_PERIOD + phase) * BOB_HEIGHT, 0.0F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(time / TURN_PERIOD + phase));
        itemRenderer.renderItem(stack, ModelTransformationMode.GROUND, light, OverlayTexture.DEFAULT_UV, matrices,
                vertexConsumers, entity.getWorld(), entity.getId());
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(BlackRavenFeatherEntity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}
