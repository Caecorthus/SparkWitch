package dev.caecorthus.sparkwitch.client.witchmaiden;

import dev.caecorthus.sparkwitch.client.bedrock.BedrockBounds;
import dev.caecorthus.sparkwitch.client.bedrock.BedrockPose;
import dev.caecorthus.sparkwitch.client.bedrock.geo.BedrockModelRenderer;
import dev.caecorthus.sparkwitch.client.witchmaiden.skin.MiniWitherSkin;
import dev.caecorthus.sparkwitch.client.witchmaiden.skin.MiniWitherSkinAnimator;
import dev.caecorthus.sparkwitch.client.witchmaiden.skin.MiniWitherSkinFit;
import dev.caecorthus.sparkwitch.client.witchmaiden.skin.MiniWitherSkinManager;
import dev.caecorthus.sparkwitch.client.witchmaiden.skin.MiniWitherSkinModel;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherRules;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherSkullEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.SkullEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;

/**
 * Vanilla's wither skull model, scaled down for the Mini Wither and textured by kind: the black skull, the blue one for
 * a Conscience owner, and the crimson one, which is the blue texture tinted red at draw time (no recoloured copy of a
 * vanilla texture ships). Always fully lit, like vanilla's skull. A selected Mini Wither skin with a skull model
 * replaces it on this client only, fitted to the vanilla skull's box and keeping the three kinds apart (own textures,
 * else tints).
 * 原版凋灵之首模型，按小凋灵缩小，并按种类使用贴图：黑色头颅、主人善良时的蓝色头颅，以及赤色头颅——绘制时把蓝色贴图染成红色
 * （不随附任何原版贴图的改色副本）。与原版头颅一样始终全亮。选中的小凋灵皮肤若带有头颅模型，只在本客户端替换它：适配原版
 * 头颅的包围盒，并保持三种头颅可区分（使用各自贴图，否则染色）。
 */
public class MiniWitherSkullEntityRenderer extends EntityRenderer<MiniWitherSkullEntity> {
    private static final Identifier NORMAL_TEXTURE = Identifier.ofVanilla("textures/entity/wither/wither.png");
    private static final Identifier BLESSED_TEXTURE =
            Identifier.ofVanilla("textures/entity/wither/wither_invulnerable.png");
    /** ARGB multiplier that turns the blue skull crimson. / 把蓝色头颅染成赤色的 ARGB 乘数。 */
    private static final int CRIMSON_TINT = 0xFFFF3A2E;
    /** ARGB multiplier for a skin's blessed skull without its own texture. / 无专属贴图时善良头颅的 ARGB 乘数。 */
    private static final int BLESSED_TINT = 0xFF7FB8FF;
    private static final float SCALE = 0.6F;
    private static final float MIN_SKIN_SCALE = 1.0E-4F;
    /**
     * The vanilla skull (an 8 px cube resting on its pivot) in the y-up turned frame, the skin auto-fit target.
     * 原版头颅（立在枢轴上的 8 像素立方体）在 y 向上的转向后坐标系中的包围盒，即皮肤自动缩放的目标。
     */
    private static final BedrockBounds SKULL_BOUNDS = new BedrockBounds(-4.0F / 16.0F * SCALE, 0.0F,
            -4.0F / 16.0F * SCALE, 4.0F / 16.0F * SCALE, 8.0F / 16.0F * SCALE, 4.0F / 16.0F * SCALE);

    private final SkullEntityModel model;

    public MiniWitherSkullEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.model = new SkullEntityModel(context.getPart(EntityModelLayers.WITHER_SKULL));
    }

    @Override
    protected int getBlockLight(MiniWitherSkullEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    public void render(MiniWitherSkullEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        if (passesOwnCamera(entity, tickDelta)) {
            return;
        }
        float headYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevYaw, entity.getYaw());
        float headPitch = MathHelper.lerp(tickDelta, entity.prevPitch, entity.getPitch());
        MiniWitherSkin skin = MiniWitherSkinManager.active();
        MiniWitherSkinModel skull = skin == null ? null : skin.skull();
        if (skull == null || !renderSkin(entity, skull, headYaw, headPitch, tickDelta, matrices, vertexConsumers,
                light)) {
            matrices.push();
            matrices.scale(-SCALE, -SCALE, SCALE);
            model.setHeadRotation(0.0F, headYaw, headPitch);
            model.render(matrices, vertexConsumers.getBuffer(model.getLayer(getTexture(entity))), light,
                    OverlayTexture.DEFAULT_UV, entity.kind() == MiniWitherSkullEntity.Kind.CRIMSON ? CRIMSON_TINT : -1);
            matrices.pop();
        }
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    /**
     * The skin's skull, turned like the vanilla head part (about its base centre) and fitted to the vanilla skull's box;
     * false hands the draw back to vanilla. A throwing skin is dropped to vanilla until it is reloaded.
     * 皮肤的头颅：像原版头部一样（绕底面中心）转向，并适配原版头颅的包围盒；返回 false 时交还原版绘制。抛出异常的皮肤会回退到
     * 原版，直到重新加载。
     */
    private static boolean renderSkin(MiniWitherSkullEntity entity, MiniWitherSkinModel skull, float headYaw,
                                      float headPitch, float tickDelta, MatrixStack matrices,
                                      VertexConsumerProvider vertexConsumers, int light) {
        try {
            BedrockPose pose = MiniWitherSkinAnimator.poseSkull(skull, entity, tickDelta);
            MiniWitherSkinFit.Transform fit = MiniWitherSkinFit.cap(
                    MiniWitherSkinFit.fit(SKULL_BOUNDS, skull.restBounds()), SKULL_BOUNDS,
                    BedrockModelRenderer.bounds(skull.geometry(), pose));
            if (fit.scale() <= MIN_SKIN_SCALE) {
                return true;
            }
            Identifier texture = skull.texture();
            int tint = -1;
            switch (entity.kind()) {
                case BLESSED -> {
                    texture = skull.blessedTexture() != null ? skull.blessedTexture() : texture;
                    tint = skull.blessedTexture() != null ? -1 : BLESSED_TINT;
                }
                case CRIMSON -> {
                    texture = skull.crimsonTexture() != null ? skull.crimsonTexture()
                            : skull.blessedTexture() != null ? skull.blessedTexture() : texture;
                    tint = skull.crimsonTexture() != null ? -1 : CRIMSON_TINT;
                }
                default -> {
                }
            }
            matrices.push();
            try {
                // The vanilla head part turns in ModelPart space (y down); swap there and back.
                // 原版头部在 ModelPart 空间（y 向下）中转向；切换过去再切换回来。
                matrices.scale(-1.0F, -1.0F, 1.0F);
                matrices.multiply(new Quaternionf().rotationZYX(0.0F, headYaw * MathHelper.RADIANS_PER_DEGREE,
                        headPitch * MathHelper.RADIANS_PER_DEGREE));
                matrices.scale(-1.0F, -1.0F, 1.0F);
                matrices.translate(fit.x(), fit.y(), fit.z());
                matrices.scale(fit.scale(), fit.scale(), fit.scale());
                RenderLayer layer = skull.translucent() ? RenderLayer.getEntityTranslucent(texture)
                        : RenderLayer.getEntityCutoutNoCull(texture);
                BedrockModelRenderer.render(skull.geometry(), pose, matrices, vertexConsumers.getBuffer(layer), light,
                        OverlayTexture.DEFAULT_UV, tint);
            } finally {
                matrices.pop();
            }
            return true;
        } catch (RuntimeException failure) {
            MiniWitherSkinManager.markBroken(failure);
            return false;
        }
    }

    /**
     * Her own skull right after launch, in her own first person: it starts behind her head and would cover the screen
     * (vanilla hides thrown items near the camera the same way).
     * 刚发射、仍在她自己第一人称镜头附近的自己的头颅：它从脑后出发，会挡住整个屏幕（原版对镜头附近的投掷物也这样隐藏）。
     */
    private boolean passesOwnCamera(MiniWitherSkullEntity entity, float tickDelta) {
        Camera camera = dispatcher.camera;
        return camera != null && !camera.isThirdPerson() && entity.getOwner() != null
                && entity.getOwner() == camera.getFocusedEntity()
                && MiniWitherRules.withinOwnerClearance(camera.getPos(), entity.getLerpedPos(tickDelta));
    }

    @Override
    public Identifier getTexture(MiniWitherSkullEntity entity) {
        return switch (entity.kind()) {
            case BLESSED, CRIMSON -> BLESSED_TEXTURE;
            default -> NORMAL_TEXTURE;
        };
    }
}
