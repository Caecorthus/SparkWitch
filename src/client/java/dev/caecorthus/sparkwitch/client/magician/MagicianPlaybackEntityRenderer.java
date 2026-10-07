package dev.caecorthus.sparkwitch.client.magician;

import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;

/**
 * 魔术师皮套的玩家外观渲染器。
 *
 * <p>皮套实体本身是 LivingEntity，不会自动走 PlayerEntityRenderer 的玩家外观逻辑，
 * 因此这里显式补齐 Steve/Alex 模型、手持物特性层和玩家手臂姿势。</p>
 */
public final class MagicianPlaybackEntityRenderer extends LivingEntityRenderer<MagicianPlaybackEntity, PlayerEntityModel<MagicianPlaybackEntity>> {
    // 自改版 NoellesRoles 的皮套略小于原版玩家模型；只缩放视觉，不改变服务端碰撞箱。
    private static final float PLAYBACK_VISUAL_SCALE = 0.96F;
    private static final float PLAYBACK_GROUND_OFFSET = -0.045F;
    private final MagicianPlaybackPlayerEntityModel classicModel;
    private final MagicianPlaybackPlayerEntityModel slimModel;
    private @Nullable MagicianPlaybackEntity renderingEntity;
    private @Nullable MagicianPuppetAppearance renderingAppearance;

    public MagicianPlaybackEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new MagicianPlaybackPlayerEntityModel(context.getPart(EntityModelLayers.PLAYER), false), 0.5F);
        this.classicModel = (MagicianPlaybackPlayerEntityModel) this.getModel();
        this.slimModel = new MagicianPlaybackPlayerEntityModel(context.getPart(EntityModelLayers.PLAYER_SLIM), true);
        // LivingEntityRenderer 不会自动添加玩家的手持物层，必须显式挂载，否则同步装备不可见。
        this.addFeature(new HeldItemFeatureRenderer<>(this, context.getHeldItemRenderer()));
        this.addFeature(new MagicianPlaybackCapeFeatureRenderer());
    }

    @Override
    public void render(MagicianPlaybackEntity entity, float entityYaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        // Resolved once per frame and shared by getTexture and the cape layer (owner decision D6).
        // 每帧解析一次，供 getTexture 与披风层共用（所有者决定 D6）。
        MagicianPuppetAppearance appearance = MagicianPuppetAppearance.resolve(entity);
        this.model = appearance.model() == SkinTextures.Model.SLIM ? slimModel : classicModel;
        setModelPose(entity);
        matrices.push();
        matrices.translate(0.0F, PLAYBACK_GROUND_OFFSET, 0.0F);
        matrices.scale(PLAYBACK_VISUAL_SCALE, PLAYBACK_VISUAL_SCALE, PLAYBACK_VISUAL_SCALE);
        this.renderingEntity = entity;
        this.renderingAppearance = appearance;
        try {
            super.render(entity, entityYaw, tickDelta, matrices, vertexConsumers, light);
        } finally {
            this.renderingEntity = null;
            this.renderingAppearance = null;
            matrices.pop();
        }
    }

    @Override
    public Identifier getTexture(MagicianPlaybackEntity entity) {
        return appearance(entity).texture();
    }

    @Override
    protected boolean hasLabel(MagicianPlaybackEntity entity) {
        return false;
    }

    /** 把录制帧中的使用状态转换为原版玩家模型使用的左右手姿势。 */
    private void setModelPose(MagicianPlaybackEntity entity) {
        PlayerEntityModel<MagicianPlaybackEntity> model = getModel();
        model.setVisible(true);
        model.sneaking = entity.isInSneakingPose();

        BipedEntityModel.ArmPose mainPose = armPose(entity, Hand.MAIN_HAND);
        BipedEntityModel.ArmPose offPose = armPose(entity, Hand.OFF_HAND);
        if (mainPose.isTwoHanded()) {
            offPose = entity.getOffHandStack().isEmpty()
                    ? BipedEntityModel.ArmPose.EMPTY
                    : BipedEntityModel.ArmPose.ITEM;
        }
        if (entity.getMainArm() == Arm.RIGHT) {
            model.rightArmPose = mainPose;
            model.leftArmPose = offPose;
        } else {
            model.rightArmPose = offPose;
            model.leftArmPose = mainPose;
        }
    }

    private static BipedEntityModel.ArmPose armPose(MagicianPlaybackEntity entity, Hand hand) {
        ItemStack stack = entity.getStackInHand(hand);
        if (stack.isEmpty()) return BipedEntityModel.ArmPose.EMPTY;

        if (entity.isReplayUsingItem() && entity.getReplayActiveHand() == hand) {
            UseAction action = stack.getUseAction();
            return switch (action) {
                case BLOCK -> BipedEntityModel.ArmPose.BLOCK;
                case BOW -> BipedEntityModel.ArmPose.BOW_AND_ARROW;
                case SPEAR -> BipedEntityModel.ArmPose.THROW_SPEAR;
                case CROSSBOW -> BipedEntityModel.ArmPose.CROSSBOW_CHARGE;
                case SPYGLASS -> BipedEntityModel.ArmPose.SPYGLASS;
                case TOOT_HORN -> BipedEntityModel.ArmPose.TOOT_HORN;
                case BRUSH -> BipedEntityModel.ArmPose.BRUSH;
                default -> BipedEntityModel.ArmPose.ITEM;
            };
        }
        if (!entity.handSwinging && stack.isOf(Items.CROSSBOW) && CrossbowItem.isCharged(stack)) {
            return BipedEntityModel.ArmPose.CROSSBOW_HOLD;
        }
        return BipedEntityModel.ArmPose.ITEM;
    }

    /**
     * The look resolved for the entity being rendered, or a fresh one outside {@link #render}.
     * 正在渲染的实体已解析的外观；在 {@link #render} 之外则重新解析。
     */
    private MagicianPuppetAppearance appearance(MagicianPlaybackEntity entity) {
        MagicianPuppetAppearance current = this.renderingAppearance;
        return current != null && this.renderingEntity == entity ? current : MagicianPuppetAppearance.resolve(entity);
    }

    /** 玩家实体没有真的挂在坐骑上，坐姿必须由录制帧显式驱动模型。 */
    private static final class MagicianPlaybackPlayerEntityModel extends PlayerEntityModel<MagicianPlaybackEntity> {
        private MagicianPlaybackPlayerEntityModel(net.minecraft.client.model.ModelPart root, boolean thinArms) {
            super(root, thinArms);
        }

        @Override
        public void setAngles(MagicianPlaybackEntity entity, float limbAngle, float limbDistance,
                              float animationProgress, float headYaw, float headPitch) {
            this.riding = entity.isReplaySitting();
            super.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
        }
    }

    /** 目标玩家有披风时复用其纹理。 */
    private final class MagicianPlaybackCapeFeatureRenderer extends FeatureRenderer<MagicianPlaybackEntity, PlayerEntityModel<MagicianPlaybackEntity>> {
        private MagicianPlaybackCapeFeatureRenderer() { super(MagicianPlaybackEntityRenderer.this); }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                           MagicianPlaybackEntity entity, float limbAngle, float limbDistance, float tickDelta,
                           float animationProgress, float headYaw, float headPitch) {
            Identifier cape = appearance(entity).cape();
            if (cape == null || entity.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) return;
            matrices.push();
            matrices.translate(0.0F, 0.0F, 0.125F);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(entity.isInSneakingPose() ? 31.0F : 12.0F));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
            getContextModel().renderCape(matrices, vertexConsumers.getBuffer(RenderLayer.getEntitySolid(cape)), light, OverlayTexture.DEFAULT_UV);
            matrices.pop();
        }
    }
}
