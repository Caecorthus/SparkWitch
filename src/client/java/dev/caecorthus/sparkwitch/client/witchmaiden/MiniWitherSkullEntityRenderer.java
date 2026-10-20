package dev.caecorthus.sparkwitch.client.witchmaiden;

import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherRules;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherSkullEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.SkullEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/**
 * Vanilla's wither skull model, scaled down for the Mini Wither and textured by kind: the black skull, the blue one for
 * a Conscience owner, and the crimson one, which is the blue texture tinted red at draw time (no recoloured copy of a
 * vanilla texture ships). Always fully lit, like vanilla's skull.
 * 原版凋灵之首模型，按小凋灵缩小，并按种类使用贴图：黑色头颅、主人善良时的蓝色头颅，以及赤色头颅——绘制时把蓝色贴图染成红色
 * （不随附任何原版贴图的改色副本）。与原版头颅一样始终全亮。
 */
public class MiniWitherSkullEntityRenderer extends EntityRenderer<MiniWitherSkullEntity> {
    private static final Identifier NORMAL_TEXTURE = Identifier.ofVanilla("textures/entity/wither/wither.png");
    private static final Identifier BLESSED_TEXTURE =
            Identifier.ofVanilla("textures/entity/wither/wither_invulnerable.png");
    /** ARGB multiplier that turns the blue skull crimson. / 把蓝色头颅染成赤色的 ARGB 乘数。 */
    private static final int CRIMSON_TINT = 0xFFFF3A2E;
    private static final float SCALE = 0.6F;

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
        matrices.push();
        matrices.scale(-SCALE, -SCALE, SCALE);
        float headYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevYaw, entity.getYaw());
        float headPitch = MathHelper.lerp(tickDelta, entity.prevPitch, entity.getPitch());
        model.setHeadRotation(0.0F, headYaw, headPitch);
        model.render(matrices, vertexConsumers.getBuffer(model.getLayer(getTexture(entity))), light,
                OverlayTexture.DEFAULT_UV, entity.kind() == MiniWitherSkullEntity.Kind.CRIMSON ? CRIMSON_TINT : -1);
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
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
