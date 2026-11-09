package dev.caecorthus.sparkwitch.client.bear.launcher;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.potiongunner.PotionShellModelPose;
import dev.caecorthus.sparkwitch.client.renderer.ProjectileModelPose;
import dev.caecorthus.sparkwitch.client.renderer.ProjectileModels;
import dev.caecorthus.sparkwitch.roles.civilian.bear.launcher.SmokeRoundEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * Client only, presentation only: draws the flying 40 mm smoke round as its standalone 3D model
 * ({@code models/item/projectile/smoke_round_40mm.json}, the {@link ProjectileModels} contract: nose -Z, pivot
 * (8, 8, 8)) instead of a camera-facing sprite, nose along the client-predicted velocity, spinning slowly about its own
 * axis (a 40 mm round is spin-stabilised) and still once it lies at rest. Like vanilla {@code FlyingItemEntityRenderer}
 * it is hidden for its first two ticks near the camera ({@link #hiddenAtLaunch}), so it never flashes across the
 * shooter's screen while everyone else still sees it leave the muzzle.
 * 仅客户端、仅展示：把飞行中的 40 mm 烟雾榴弹绘制为独立 3D 模型（{@code models/item/projectile/smoke_round_40mm.json}，遵循
 * {@link ProjectileModels} 约定：头部 -Z、枢轴 (8, 8, 8)），取代面向镜头的贴图；弹头沿客户端预测的速度方向，绕自身轴缓慢自旋
 * （40 mm 榴弹靠旋转稳定），静止后不再转动。与原版 {@code FlyingItemEntityRenderer} 一样，在镜头附近的前两刻不绘制
 * （{@link #hiddenAtLaunch}），避免闪过射手屏幕，其他人仍能看到它离开炮口。
 */
public final class SmokeRoundEntityRenderer extends EntityRenderer<SmokeRoundEntity> {
    public static final Identifier MODEL_ID = SparkWitch.id("item/projectile/smoke_round_40mm");
    /** The art's suggested scale: about 0.15 blocks across. / 美术建议的缩放：直径约 0.15 格。 */
    static final float MODEL_SCALE = 0.6F;
    static final float SPIN_DEGREES_PER_TICK = 18.0F;
    private static boolean modelsRegistered;

    public SmokeRoundEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    /** Adds the standalone model to the model bakery once. / 只向模型烘焙加入一次独立模型。 */
    public static synchronized void registerModels() {
        if (modelsRegistered) {
            return;
        }
        modelsRegistered = true;
        ProjectileModels.register(MODEL_ID);
    }

    @Override
    public void render(SmokeRoundEntity round, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        if (hiddenAtLaunch(round.age, dispatcher.camera.getPos(), round.getLerpedPos(tickDelta))) {
            return;
        }
        matrices.push();
        matrices.translate(0.0D, round.getHeight() / 2.0D, 0.0D);
        Vec3d direction = round.getVelocity();
        if (ProjectileModelPose.isDegenerate(direction.x, direction.y, direction.z)) {
            direction = PotionShellModelPose.directionFromProjectileAngles(round.getYaw(tickDelta),
                    round.getPitch(tickDelta));
        }
        ProjectileModels.faceAlong(matrices, direction.x, direction.y, direction.z);
        if (!round.isResting()) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rollDegrees(round.age, tickDelta)));
        }
        matrices.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        ProjectileModels.render(ProjectileModels.model(MODEL_ID), matrices,
                vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE)),
                light);
        matrices.pop();
        super.render(round, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    /**
     * Vanilla's launch hide ({@code age < 2} within 3.5 blocks, {@link PotionShellModelPose#hiddenAtLaunch}), measured
     * from the camera to where the round is drawn this frame, like the Ninja hook head. Vanilla measures the focused
     * entity's feet to the tick position, which a 3.8 b/t round leaves on its first client tick while the interpolated
     * model still sits in front of the shooter's eye (one frame of a screen-filling round). Other viewers are far from
     * it, so they still see it leave the muzzle.
     * 原版出膛隐藏（{@code age < 2} 且在 3.5 格内，{@link PotionShellModelPose#hiddenAtLaunch}），但与忍者钩头一样，按镜头
     * 到本帧绘制位置（插值后）的距离计算。原版用聚焦实体脚下到本刻位置的距离；3.8 格/刻的榴弹在客户端第一刻就已越出该
     * 范围，而插值后的模型仍停在射手眼前（一帧铺满屏幕的榴弹）。其他观察者离它很远，仍能看到它离开炮口。
     */
    static boolean hiddenAtLaunch(int age, Vec3d cameraPos, Vec3d renderPos) {
        return PotionShellModelPose.hiddenAtLaunch(age, cameraPos.squaredDistanceTo(renderPos));
    }

    /** Right-hand spin, wrapped to (-360, 0]. / 右旋自旋，限制在 (-360, 0]。 */
    static float rollDegrees(int age, float tickDelta) {
        float turned = ((age + tickDelta) * SPIN_DEGREES_PER_TICK) % 360.0F;
        return turned == 0.0F ? 0.0F : -turned;
    }

    @Override
    public Identifier getTexture(SmokeRoundEntity round) {
        return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
    }
}
