package dev.caecorthus.sparkwitch.client.witchmaiden;

import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherAnchor;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherPlayerComponent;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.model.WitherEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

/**
 * Draws a summoned Mini Wither: the vanilla Wither model and texture at 0.4 model scale (about 0.7 blocks), softly
 * following {@link MiniWitherAnchor} ({@link MiniWitherFollow}) and tilting into its flight. The body faces the owner's
 * body yaw; the three heads keep staring the way the last skull flew and snap to each new shot (owner 2026-10-20).
 * Other players get it from a player-renderer feature, so anything that hides a player (invisibility, the Blind gate)
 * hides it too; the camera's own player, which first person never renders, gets it in world space (SparkStrength
 * skateboard pattern).
 * 绘制已召唤的小凋灵：原版凋灵模型与贴图，模型缩放 0.4（约 0.7 格高），柔性跟随 {@link MiniWitherAnchor}
 * （{@link MiniWitherFollow}）并随飞行倾斜。身体朝向主人的身体偏航；三个头一直盯着上一枚头颅飞去的方向，每次射击时瞬间转到
 * 新方向（所有者 2026-10-20）。其他玩家由玩家渲染器特征绘制，因此任何隐藏玩家的效果（隐身、盲人遮蔽）也会隐藏它；第一人称
 * 不渲染的镜头玩家本人改在世界空间绘制（沿用 SparkStrength 滑板的做法）。
 */
public final class MiniWitherRenderer {
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/wither/wither.png");
    private static final float MODEL_SCALE = 0.4F;
    /** Model-space height (blocks) of the body centre, put on the anchor. / 放在悬浮点上的身体中心的模型高度（格）。 */
    private static final float MODEL_CENTER = 0.55F;
    /** {@code PlayerEntityRenderer#scale}. / 玩家渲染器的模型缩放。 */
    private static final float PLAYER_MODEL_SCALE = 0.9375F;
    /** {@code LivingEntityRenderer#render} lowers the model by this after scaling. / 生物渲染器缩放后下移模型的距离。 */
    private static final float MODEL_FOOT_OFFSET = 1.501F;
    private static final float SIDE_HEAD_YAW = 0.35F;
    /** Flight tilt: degrees per block-per-tick of drawn speed, capped. / 飞行倾斜：每格/刻绘制速度对应的角度，有上限。 */
    private static final float TILT_PER_SPEED = 60.0F;
    private static final float MAX_TILT = 15.0F;

    private static WitherEntityModel<WitherEntity> model;

    private MiniWitherRenderer() {
    }

    /** Whether {@code player} visibly carries a Mini Wither right now. / 该玩家此刻是否带着可见的小凋灵。 */
    public static boolean shows(PlayerEntity player) {
        return !player.isSpectator() && !player.isInvisible() && MiniWitherPlayerComponent.KEY.get(player).isActive();
    }

    /** First person never renders the camera's player, so its Wither is drawn here. / 第一人称不渲染镜头玩家，因此在此绘制。 */
    static void renderCameraWither(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || context.camera().isThirdPerson()
                || !(client.getCameraEntity() instanceof PlayerEntity player) || !shows(player)) {
            return;
        }
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) {
            return;
        }
        float tickDelta = context.tickCounter().getTickDelta(false);
        Vec3d anchor = MiniWitherFollow.position(player, tickDelta);
        Vec3d camera = context.camera().getPos();
        matrices.push();
        matrices.translate(anchor.x - camera.x, anchor.y - camera.y, anchor.z - camera.z);
        draw(matrices, consumers, light(player, anchor), player, tickDelta);
        matrices.pop();
    }

    /**
     * Expects a world-aligned frame at the anchor. The model is posed by hand (no Wither entity exists): vanilla's idle
     * ribcage and tail sway, and the heads on the synced last-shot direction, never interpolated, so a new shot snaps
     * them round. The whole body leans toward its drawn velocity.
     * 需要位于悬浮点、与世界坐标轴对齐的坐标系。模型手动摆姿（不存在凋灵实体）：原版待机的肋骨与尾巴摆动，三个头朝向同步的
     * 上一次射击方向且从不插值，因此新的射击会让它们瞬间扭过去。整个身体朝绘制速度方向倾斜。
     */
    private static void draw(MatrixStack matrices, VertexConsumerProvider consumers, int light, PlayerEntity player,
                             float tickDelta) {
        WitherEntityModel<WitherEntity> wither = model();
        float yaw = MiniWitherAnchor.bodyYaw(player, tickDelta);
        MiniWitherPlayerComponent state = MiniWitherPlayerComponent.KEY.get(player);
        float headYaw = MathHelper.wrapDegrees(state.getAimYaw() - yaw);
        pose(wither.getPart(), player.age + tickDelta, headYaw, state.getAimPitch());
        matrices.push();
        tilt(matrices, MiniWitherFollow.velocity(player));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - yaw));
        matrices.scale(-MODEL_SCALE, -MODEL_SCALE, MODEL_SCALE);
        matrices.translate(0.0F, -MODEL_CENTER, 0.0F);
        wither.render(matrices, consumers.getBuffer(wither.getLayer(TEXTURE)), light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }

    /** Leans the top toward the horizontal drawn velocity. / 让顶部朝水平绘制速度方向倾斜。 */
    private static void tilt(MatrixStack matrices, Vec3d velocity) {
        double speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (speed < 1.0E-4) {
            return;
        }
        float degrees = Math.min(MAX_TILT, (float) speed * TILT_PER_SPEED);
        // Axis = up x direction, so +Y rotates toward the motion. / 轴 = 上方 x 方向，使 +Y 转向运动方向。
        matrices.multiply(new Quaternionf().rotateAxis(degrees * MathHelper.RADIANS_PER_DEGREE,
                (float) (velocity.z / speed), 0.0F, (float) (-velocity.x / speed)));
    }

    private static void pose(ModelPart root, float animationProgress, float headYaw, float headPitch) {
        float sway = MathHelper.cos(animationProgress * 0.1F);
        ModelPart ribcage = root.getChild("ribcage");
        ModelPart tail = root.getChild("tail");
        ribcage.pitch = (0.065F + 0.05F * sway) * (float) Math.PI;
        tail.setPivot(-2.0F, 6.9F + MathHelper.cos(ribcage.pitch) * 10.0F,
                -0.5F + MathHelper.sin(ribcage.pitch) * 10.0F);
        tail.pitch = (0.265F + 0.1F * sway) * (float) Math.PI;
        float yaw = headYaw * MathHelper.RADIANS_PER_DEGREE;
        float pitch = headPitch * MathHelper.RADIANS_PER_DEGREE;
        ModelPart center = root.getChild("center_head");
        center.yaw = yaw;
        center.pitch = pitch;
        ModelPart right = root.getChild("right_head");
        right.yaw = yaw - SIDE_HEAD_YAW;
        right.pitch = pitch;
        ModelPart left = root.getChild("left_head");
        left.yaw = yaw + SIDE_HEAD_YAW;
        left.pitch = pitch;
    }

    private static WitherEntityModel<WitherEntity> model() {
        if (model == null) {
            model = new WitherEntityModel<>(MinecraftClient.getInstance().getEntityModelLoader()
                    .getModelPart(EntityModelLayers.WITHER));
        }
        return model;
    }

    private static int light(Entity owner, Vec3d at) {
        return WorldRenderer.getLightmapCoordinates(owner.getWorld(), BlockPos.ofFloored(at));
    }

    /** Mini Wither on other players (and the own player in third person). / 其他玩家（及第三人称的本人）身上的小凋灵。 */
    static final class Feature
            extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
        private final PlayerEntityRenderer renderer;

        Feature(PlayerEntityRenderer renderer) {
            super(renderer);
            this.renderer = renderer;
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                           AbstractClientPlayerEntity player, float limbAngle, float limbDistance, float tickDelta,
                           float animationProgress, float headYaw, float headPitch) {
            float scale = player.getScale();
            if (scale <= 0.0F || !shows(player)) {
                return;
            }
            // Feature space = T(feet + positionOffset) * S(scale) * rotY(180 - bodyYaw) * S(-1, -1, 1) * S(0.9375)
            // * T(0, -1.501, 0). Peel it back to a world-aligned frame at the feet, then move to the anchor.
            // 特征空间 = T(脚 + 位置偏移) * S(缩放) * rotY(180 - 身体偏航) * S(-1, -1, 1) * S(0.9375) * T(0, -1.501, 0)。
            // 还原到脚下的世界对齐坐标系，再移到悬浮点。
            matrices.push();
            matrices.translate(0.0F, MODEL_FOOT_OFFSET, 0.0F);
            matrices.scale(1.0F / PLAYER_MODEL_SCALE, 1.0F / PLAYER_MODEL_SCALE, 1.0F / PLAYER_MODEL_SCALE);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180.0F));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MiniWitherAnchor.bodyYaw(player, tickDelta)));
            Vec3d feet = new Vec3d(
                    MathHelper.lerp(tickDelta, player.lastRenderX, player.getX()),
                    MathHelper.lerp(tickDelta, player.lastRenderY, player.getY())
                            + renderer.getPositionOffset(player, tickDelta).y,
                    MathHelper.lerp(tickDelta, player.lastRenderZ, player.getZ()));
            Vec3d anchor = MiniWitherFollow.position(player, tickDelta);
            Vec3d offset = anchor.subtract(feet).multiply(1.0 / scale);
            matrices.translate(offset.x, offset.y, offset.z);
            matrices.scale(1.0F / scale, 1.0F / scale, 1.0F / scale);
            draw(matrices, vertexConsumers, light(player, anchor), player, tickDelta);
            matrices.pop();
        }
    }
}
