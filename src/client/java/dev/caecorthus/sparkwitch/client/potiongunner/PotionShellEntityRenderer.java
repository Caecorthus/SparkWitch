package dev.caecorthus.sparkwitch.client.potiongunner;

import dev.caecorthus.sparkwitch.client.renderer.ProjectileModelPose;
import dev.caecorthus.sparkwitch.client.renderer.ProjectileModels;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionShellEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * Client only, presentation only: draws the flying Potion Gunner shell as its 3D model (replacing the camera-facing
 * item sprite), nose along the client-predicted velocity, spinning about its own axis, with an additive nozzle glow.
 * The model follows the synced stack's type ({@link PotionShellEntity#renderType()}, TR when the stack is not a shell),
 * and like vanilla {@code FlyingItemEntityRenderer} the shell is hidden for its first two ticks near the camera entity.
 * 仅客户端、仅展示：把飞行中的药炮手炮弹绘制为 3D 模型（取代面向镜头的物品贴图），弹头沿客户端预测的速度方向，绕自身
 * 轴自旋，并带加色的喷口尾焰。模型跟随同步物品的种类（{@link PotionShellEntity#renderType()}，物品不是炮弹时为 TR）；
 * 与原版 {@code FlyingItemEntityRenderer} 一样，炮弹在镜头实体附近的前两刻不绘制。
 */
public final class PotionShellEntityRenderer extends EntityRenderer<PotionShellEntity> {
    /**
     * Model pixels to world: the 14-pixel shell is about 0.66 blocks long and its warhead about as wide as the
     * launcher's held muzzle. / 模型像素到世界的缩放：14 像素的炮弹约 0.66 格长，弹头与手持炮筒口差不多宽。
     */
    private static final float MODEL_SCALE = 0.75F;

    public PotionShellEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(PotionShellEntity shell, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        Entity focused = dispatcher.camera.getFocusedEntity();
        double focusedDistanceSquared = focused == null ? Double.POSITIVE_INFINITY : focused.squaredDistanceTo(shell);
        if (PotionShellModelPose.hiddenAtLaunch(shell.age, focusedDistanceSquared)) {
            return;
        }
        matrices.push();
        matrices.translate(0.0D, shell.getHeight() / 2.0D, 0.0D);
        Vec3d direction = shell.getVelocity();
        if (ProjectileModelPose.isDegenerate(direction.x, direction.y, direction.z)) {
            direction = PotionShellModelPose.directionFromProjectileAngles(shell.getYaw(tickDelta),
                    shell.getPitch(tickDelta));
        }
        ProjectileModels.faceAlong(matrices, direction.x, direction.y, direction.z);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(
                PotionShellModelPose.rollDegrees(shell.age, tickDelta)));
        matrices.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        // Not getEntityCutout(BLOCK_ATLAS): that is one of the fixed entity buffers, drawn after every entity, so the
        // body would paint over the glow below. This layer is immediate and flushes when the eyes buffer is requested.
        // 不用 getEntityCutout(BLOCK_ATLAS)：它是固定实体缓冲之一，在所有实体之后才绘制，弹体会盖掉下面的尾焰。本层为即时层，
        // 请求 eyes 缓冲时先行提交。
        ProjectileModels.render(ProjectileModels.model(PotionShellModels.bodyModelId(shell.renderType())), matrices,
                vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE)),
                light);
        // Eyes layer: additive and full bright, depth-tested against the body but never written, so it only lifts the
        // nozzle. / 眼睛渲染层：加色且全亮，与弹体做深度测试但不写深度，只提亮喷口。
        ProjectileModels.render(ProjectileModels.model(PotionShellModels.FLARE_MODEL_ID), matrices,
                vertexConsumers.getBuffer(RenderLayer.getEyes(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE)),
                LightmapTextureManager.MAX_LIGHT_COORDINATE);
        matrices.pop();
        super.render(shell, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(PotionShellEntity shell) {
        return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
    }
}
