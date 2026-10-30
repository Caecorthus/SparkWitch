package dev.caecorthus.sparkwitch.client.renderer;

import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;

import java.util.List;

/**
 * Client only: standalone 3D projectile models (the Potion Gunner shell, the Ninja grappling hook). Each is a JSON
 * model no item points at, loaded through Fabric's ModelLoadingPlugin like the Hunter trap and the Rift Gate, and drawn
 * by hand as baked quads. Authoring contract: 0..16 model space, pivot (8, 8, 8), nose pointing north (-Z), textures
 * under {@code textures/item/} so they are stitched into the block atlas.
 * 仅客户端：独立的 3D 投射物模型（药炮手炮弹、忍者钩爪）。每个都是没有物品引用的 JSON 模型，与猎人捕兽夹、裂隙门一样
 * 经 Fabric ModelLoadingPlugin 加载，并手动提交 baked quad 绘制。建模约定：0..16 模型空间，枢轴 (8, 8, 8)，头部朝北
 * （-Z），贴图放在 {@code textures/item/} 下以拼入方块图集。
 */
public final class ProjectileModels {
    private static final long QUAD_RANDOM_SEED = 42L;
    // Render thread only; reseeded before every getQuads call, so it behaves like a fresh Random each time.
    // 仅渲染线程使用；每次 getQuads 前重新设种子，效果等同每次新建 Random。
    private static final Random QUAD_RANDOM = Random.create();
    private static final Direction[] DIRECTIONS = Direction.values();

    private ProjectileModels() {
    }

    /** Adds standalone models to the model bakery; call once from client init. / 把独立模型加入模型烘焙；客户端初始化时调用一次。 */
    public static void register(Identifier... modelIds) {
        Identifier[] ids = modelIds.clone();
        ModelLoadingPlugin.register(context -> context.addModels(ids));
    }

    /** The baked model, or the missing model when it failed to load. / 烘焙后的模型；加载失败时为缺失模型。 */
    public static BakedModel model(Identifier modelId) {
        return ((FabricBakedModelManager) MinecraftClient.getInstance().getBakedModelManager()).getModel(modelId);
    }

    /**
     * Turns the model's nose (north, -Z) onto {@code (dx, dy, dz)} around the current origin; a degenerate direction
     * leaves the stack as it is. / 绕当前原点把模型头部（北，-Z）转向 {@code (dx, dy, dz)}；方向退化时不改变矩阵栈。
     */
    public static void faceAlong(MatrixStack matrices, double dx, double dy, double dz) {
        if (ProjectileModelPose.isDegenerate(dx, dy, dz)) {
            return;
        }
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(ProjectileModelPose.yaw(dx, dz)));
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(ProjectileModelPose.pitch(dx, dy, dz)));
    }

    /**
     * Draws every quad of {@code model} with its pivot (8, 8, 8) at the current origin, untinted.
     * 以当前原点为枢轴 (8, 8, 8) 绘制 {@code model} 的全部 quad，不着色。
     */
    public static void render(BakedModel model, MatrixStack matrices, VertexConsumer consumer, int light) {
        matrices.push();
        matrices.translate(-0.5F, -0.5F, -0.5F);
        MatrixStack.Entry entry = matrices.peek();
        for (Direction direction : DIRECTIONS) {
            QUAD_RANDOM.setSeed(QUAD_RANDOM_SEED);
            emit(model.getQuads(null, direction, QUAD_RANDOM), entry, consumer, light);
        }
        QUAD_RANDOM.setSeed(QUAD_RANDOM_SEED);
        emit(model.getQuads(null, null, QUAD_RANDOM), entry, consumer, light);
        matrices.pop();
    }

    private static void emit(List<BakedQuad> quads, MatrixStack.Entry entry, VertexConsumer consumer, int light) {
        for (BakedQuad quad : quads) {
            consumer.quad(entry, quad, 1.0F, 1.0F, 1.0F, 1.0F, light, OverlayTexture.DEFAULT_UV);
        }
    }
}
