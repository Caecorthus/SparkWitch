package dev.caecorthus.sparkwitch.client.renderer;

import dev.caecorthus.sparkwitch.client.hooks.HunterTrapClientHooks;
import dev.caecorthus.sparkwitch.client.hunter.HunterTrapVisibilityHelper;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterTrapEntity;
import java.util.List;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

/**
 * Renders the placed Hunter trap model while enforcing viewer-specific visibility.
 * 渲染已放置的猎人捕兽夹，并执行按观察者区分的可见性规则。
 */
public final class HunterTrapEntityRenderer extends EntityRenderer<HunterTrapEntity> {
    private static final long QUAD_RANDOM_SEED = 42L;
    /**
     * ItemRenderer 会先把 baked model 的坐标整体平移到以方块中心为原点，
     * 再由这里补偿捕兽夹贴地所需的高度。
     *
     * ItemRenderer first translates baked-model coordinates so that the block
     * center is the origin; this offset keeps the low trap model above its support.
     */
    private static final double MODEL_SUPPORT_OFFSET = 0.51D;

    public HunterTrapEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
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
        BakedModel model = ((FabricBakedModelManager) MinecraftClient.getInstance().getBakedModelManager())
                .getModel(HunterTrapClientHooks.HUNTER_TRAP_PLACED_MODEL_ID);

        matrices.push();
        // 保持与 ItemRenderer.renderItem 一致的变换顺序：
        // 1. 应用模型在 FIXED 模式下的 display 变换；
        // 2. 将 0..16 的模型坐标平移到以方块中心为原点；
        // 3. 把低矮捕兽夹抬到支撑面的上方。
        //
        // 这里不再调用 ItemRenderer.renderItem，是因为 ItemRenderer 会选择
        // item_entity_translucent_cull 渲染层。该层会进行背面裁剪，导致捕兽夹
        // 从部分角度只显示少数面。手动提交 baked quad 后可以使用
        // EntityCutoutNoCull，确保模型各方向的面都能稳定显示。
        matrices.translate(0.0D, MODEL_SUPPORT_OFFSET, 0.0D);
        model.getTransformation()
                .getTransformation(ModelTransformationMode.FIXED)
                .apply(false, matrices);
        matrices.translate(-0.5D, -0.5D, -0.5D);

        VertexConsumer consumer = vertexConsumers.getBuffer(
                RenderLayer.getEntityCutoutNoCull(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE)
        );
        Random random = Random.create();
        for (Direction direction : Direction.values()) {
            random.setSeed(QUAD_RANDOM_SEED);
            renderQuads(model.getQuads(null, direction, random), matrices, consumer, light);
        }
        random.setSeed(QUAD_RANDOM_SEED);
        renderQuads(model.getQuads(null, null, random), matrices, consumer, light);
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    /**
     * 提交一个方向上的 baked quad。
     *
     * 模型纹理已经在方块图集中，BakedQuad 自己携带正确的 sprite 和 UV，
     * 因此只需要沿用 ItemRenderer 的顶点颜色、光照和 overlay 参数即可。
     */
    private static void renderQuads(
            List<BakedQuad> quads,
            MatrixStack matrices,
            VertexConsumer consumer,
            int light
    ) {
        for (BakedQuad quad : quads) {
            consumer.quad(
                    matrices.peek(),
                    quad,
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F,
                    light,
                    OverlayTexture.DEFAULT_UV
            );
        }
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
