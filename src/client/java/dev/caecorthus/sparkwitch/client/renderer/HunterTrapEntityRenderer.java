package dev.caecorthus.sparkwitch.client.renderer;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.hooks.HunterTrapClientHooks;
import dev.caecorthus.sparkwitch.client.hunter.HunterTrapVisibilityHelper;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterTrapEntity;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;

/**
 * Renders the placed Hunter trap model while enforcing viewer-specific visibility.
 * 渲染已放置的猎人捕兽夹，并执行按观察者区分的可见性规则。
 */
public final class HunterTrapEntityRenderer extends EntityRenderer<HunterTrapEntity> {
    private final ItemRenderer itemRenderer;

    public HunterTrapEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(
            HunterTrapEntity entity,
            float yaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light
    ) {
        ItemStack stack = SparkWitchItems.hunterTrap().getDefaultStack();
        BakedModel model = ((FabricBakedModelManager) MinecraftClient.getInstance().getBakedModelManager())
                .getModel(HunterTrapClientHooks.HUNTER_TRAP_PLACED_MODEL_ID);

        matrices.push();
        // ItemRenderer centers every baked model on all axes; offset Y so the trap rests on its support.
        // ItemRenderer 会在三轴居中模型；这里补偿 Y，使捕兽夹贴在支撑面上。
        matrices.translate(0.0D, 0.51D, 0.0D);
        itemRenderer.renderItem(
                stack,
                ModelTransformationMode.FIXED,
                false,
                matrices,
                vertexConsumers,
                light,
                OverlayTexture.DEFAULT_UV,
                model
        );
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public boolean shouldRender(HunterTrapEntity entity, Frustum frustum, double x, double y, double z) {
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null) {
            return false;
        }
        return switch (HunterTrapVisibilityHelper.visibilityFor(entity, viewer)) {
            case HIDDEN -> false;
            case THROUGH_WALL -> true;
            case DIRECT_ONLY -> super.shouldRender(entity, frustum, x, y, z);
        };
    }

    @Override
    public Identifier getTexture(HunterTrapEntity entity) {
        return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
    }
}
