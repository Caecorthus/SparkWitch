package dev.caecorthus.sparkwitch.client.blackraven.crow;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowEntity;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowRules;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Renderer of the Raven Form crow ({@code sparkwitch:black_raven_crow}) with model B. Everyone sees it; it never has a
 * name tag. The owner sees it only in third person (vanilla skips the camera entity in first person). Speed, support
 * and turn rate drive {@link BlackRavenCrowAnimation}; the per-crow state lives here (render thread only). Presentation
 * only: the server owns the crow.
 * 化鸦乌鸦（sparkwitch:black_raven_crow）的渲染器，使用模型 B。所有人可见，从不显示名牌。主人只在第三人称看到它（原版第一人称会跳过
 * 镜头实体）。速度、是否有支撑与转向速率驱动 {@link BlackRavenCrowAnimation}；每只乌鸦的状态保存在此（仅渲染线程）。仅用于展示：
 * 乌鸦由服务端掌控。
 */
public final class BlackRavenCrowRenderer extends EntityRenderer<BlackRavenCrowEntity> {
    public static final Identifier TEXTURE = SparkWitch.id("textures/entity/black_raven_crow.png");
    /**
     * Model B is 0.75 block long with a 1.22-block glide span, larger than the 0.5-block hitbox; the art lead suggests
     * about 0.85x. Collision is unaffected. / 模型 B 长 0.75 格、滑翔翼展 1.22 格，大于 0.5 格命中箱；美术负责人建议约 0.85 倍。
     * 不影响碰撞。
     */
    public static final float MODEL_SCALE = 0.85F;
    /** Probe depth under the hull for "perched". / 判定"栖停"时机身下方的探测深度。 */
    private static final double SUPPORT_PROBE = 0.05;

    private final BlackRavenCrowModel model;
    private final Map<BlackRavenCrowEntity, BlackRavenCrowAnimation.State> states = new WeakHashMap<>();

    public BlackRavenCrowRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.model = new BlackRavenCrowModel(context.getPart(BlackRavenCrowModel.LAYER));
        this.shadowRadius = 0.2F;
    }

    @Override
    public void render(BlackRavenCrowEntity crow, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        BlackRavenCrowAnimation.State state = states.computeIfAbsent(crow, ignored -> new BlackRavenCrowAnimation.State());
        Vec3d step = new Vec3d(crow.getX() - crow.prevX, crow.getY() - crow.prevY, crow.getZ() - crow.prevZ);
        float blocksPerSecond = (float) step.length() * 20.0F;
        state.advance((crow.age + tickDelta) / 20.0, blocksPerSecond, isSupported(crow));
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, crow.prevYaw, crow.getYaw());
        float pitch = MathHelper.lerp(tickDelta, crow.prevPitch, crow.getPitch());
        float yawRate = MathHelper.wrapDegrees(crow.getYaw() - crow.prevYaw);
        model.applyPose(BlackRavenCrowAnimation.pose(state, pitch, yawRate));

        matrices.push();
        // Centre the model's root pivot on the hitbox centre, then the usual entity-model flip.
        // 把模型根枢轴对准命中箱中心，再做常规的实体模型翻转。
        matrices.translate(0.0, BlackRavenCrowRules.CROW_HEIGHT * 0.5, 0.0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - bodyYaw));
        matrices.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        matrices.scale(-1.0F, -1.0F, 1.0F);
        matrices.translate(0.0, -BlackRavenCrowAnimation.ROOT_PIVOT_Y / 16.0, 0.0);
        VertexConsumer vertices = vertexConsumers.getBuffer(model.getLayer(getTexture(crow)));
        model.render(matrices, vertices, light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
        super.render(crow, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(BlackRavenCrowEntity crow) {
        return TEXTURE;
    }

    /** The crow never shows a name tag (plan §2.5). / 乌鸦从不显示名牌（计划 §2.5）。 */
    @Override
    protected boolean hasLabel(BlackRavenCrowEntity crow) {
        return false;
    }

    private static boolean isSupported(BlackRavenCrowEntity crow) {
        Box hull = crow.getBoundingBox();
        Box below = new Box(hull.minX, hull.minY - SUPPORT_PROBE, hull.minZ, hull.maxX, hull.minY, hull.maxZ);
        return !crow.getWorld().isSpaceEmpty(crow, below);
    }
}
