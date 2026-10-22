package dev.caecorthus.sparkwitch.client.blackraven.crow;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowEntity;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;

/**
 * Crow model B ("Blackcoat crow", owner pick 2026-10-21): a horizontal crow, 12 px long with a 19.5 px glide span and a
 * 64×32 texture; the right-side parts reuse the left UVs mirrored. {@link #getTexturedModelData()} is the art lead's
 * transcription of {@code model_B.json}. Poses come from {@link BlackRavenCrowAnimation} and are absolute: every part is
 * reset to its default (= glide) each frame before they apply. Rendered with the back-face-culled
 * {@link RenderLayer#getEntityCutout} so the zero-thickness wing tips and tail fan show the right side.
 * 乌鸦模型 B（"黑衣乌鸦"，所有者 2026-10-21 选定）：水平姿态的乌鸦，长 12 像素、滑翔翼展 19.5 像素，贴图 64×32；右侧部件镜像复用左侧
 * UV。{@link #getTexturedModelData()} 为美术负责人对 model_B.json 的转写。姿态来自 {@link BlackRavenCrowAnimation} 且为绝对值：每帧
 * 先把所有部件重置为默认（= 滑翔）再应用。使用剔除背面的 getEntityCutout 渲染，零厚度的翼尖与尾扇才会显示正确的一面。
 */
public final class BlackRavenCrowModel extends SinglePartEntityModel<BlackRavenCrowEntity> {
    public static final EntityModelLayer LAYER = new EntityModelLayer(SparkWitch.id("black_raven_crow"), "main");

    private final ModelPart modelRoot;
    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart tail;
    private final ModelPart leftWing;
    private final ModelPart leftWingTip;
    private final ModelPart rightWing;
    private final ModelPart rightWingTip;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;

    public BlackRavenCrowModel(ModelPart modelRoot) {
        super(RenderLayer::getEntityCutout);
        this.modelRoot = modelRoot;
        this.root = modelRoot.getChild("root");
        this.head = root.getChild("head");
        this.tail = root.getChild("tail");
        this.leftWing = root.getChild("left_wing");
        this.leftWingTip = leftWing.getChild("left_wing_tip");
        this.rightWing = root.getChild("right_wing");
        this.rightWingTip = rightWing.getChild("right_wing_tip");
        this.leftLeg = root.getChild("left_leg");
        this.rightLeg = root.getChild("right_leg");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData modelRoot = modelData.getRoot();
        ModelPartData rootPart = modelRoot.addChild("root", ModelPartBuilder.create(),
                ModelTransform.pivot(0F, 19.5F, 0F));
        rootPart.addChild("body", ModelPartBuilder.create().uv(0, 0).cuboid(-2F, -1.5F, -2.5F, 4F, 3F, 5F)
                        .uv(18, 0).cuboid(-1.5F, 1.5F, -2.5F, 3F, 1F, 3F),
                ModelTransform.pivot(0F, 0F, 0F));
        ModelPartData headPart = rootPart.addChild("head", ModelPartBuilder.create().uv(30, 0)
                        .cuboid(-1.5F, -1.5F, -2.5F, 3F, 3F, 3F),
                ModelTransform.pivot(0F, -1F, -2F));
        headPart.addChild("beak_upper", ModelPartBuilder.create().uv(42, 0).cuboid(-0.5F, -0.5F, -2F, 1F, 1F, 2F),
                ModelTransform.pivot(0F, 0F, -2.5F));
        headPart.addChild("beak_lower", ModelPartBuilder.create().uv(48, 0).cuboid(-0.5F, 0F, -1.5F, 1F, 1F, 2F),
                ModelTransform.pivot(0F, 0.5F, -2.5F));
        rootPart.addChild("tail", ModelPartBuilder.create().uv(42, 8).cuboid(-1.5F, 0F, -0.5F, 3F, 1F, 3F)
                        .uv(0, 20).cuboid(-2.5F, 0.5F, -0.5F, 5F, 0F, 4F),
                ModelTransform.pivot(0F, -1F, 2F));
        ModelPartData leftWingPart = rootPart.addChild("left_wing", ModelPartBuilder.create().uv(0, 8)
                        .cuboid(0F, 0F, 0F, 3F, 1F, 4F),
                ModelTransform.of(2F, -1.5F, -1.5F, 0F, -0.08F, -0.12F));
        leftWingPart.addChild("left_wing_tip", ModelPartBuilder.create().uv(14, 8).cuboid(0F, 0F, 0F, 5F, 0F, 4F),
                ModelTransform.of(3F, 0.5F, 0F, 0F, -0.2F, -0.06F));
        ModelPartData rightWingPart = rootPart.addChild("right_wing", ModelPartBuilder.create().uv(0, 8).mirrored()
                        .cuboid(-3F, 0F, 0F, 3F, 1F, 4F).mirrored(false),
                ModelTransform.of(-2F, -1.5F, -1.5F, 0F, 0.08F, 0.12F));
        rightWingPart.addChild("right_wing_tip", ModelPartBuilder.create().uv(14, 8).mirrored()
                        .cuboid(-5F, 0F, 0F, 5F, 0F, 4F).mirrored(false),
                ModelTransform.of(-3F, 0.5F, 0F, 0F, 0.2F, 0.06F));
        rootPart.addChild("left_leg", ModelPartBuilder.create().uv(32, 8).cuboid(-0.5F, 0F, -0.5F, 1F, 3F, 1F)
                        .uv(36, 8).cuboid(-0.5F, 3F, -1.5F, 1F, 0F, 2F),
                ModelTransform.of(1F, 1.5F, 0.5F, 1.35F, 0F, 0F));
        rootPart.addChild("right_leg", ModelPartBuilder.create().uv(32, 8).cuboid(-0.5F, 0F, -0.5F, 1F, 3F, 1F)
                        .uv(36, 8).cuboid(-0.5F, 3F, -1.5F, 1F, 0F, 2F),
                ModelTransform.of(-1F, 1.5F, 0.5F, 1.35F, 0F, 0F));
        return TexturedModelData.of(modelData, 64, 32);
    }

    @Override
    public ModelPart getPart() {
        return modelRoot;
    }

    /** Unused: the renderer applies {@link #applyPose} with per-crow animation state. / 未使用：渲染器按每只乌鸦的状态调用 applyPose。 */
    @Override
    public void setAngles(BlackRavenCrowEntity entity, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
    }

    /** Resets every part to its default (glide) and applies one absolute pose. / 重置所有部件为默认（滑翔）并应用一个绝对姿态。 */
    public void applyPose(BlackRavenCrowAnimation.Pose pose) {
        modelRoot.traverse().forEach(ModelPart::resetTransform);
        root.pitch = pose.rootPitch();
        root.roll = pose.rootRoll();
        root.pivotY = pose.rootPivotY();
        root.pivotZ = pose.rootPivotZ();
        head.pitch = pose.headPitch();
        tail.pitch = pose.tailPitch();
        leftLeg.pitch = pose.legPitch();
        rightLeg.pitch = pose.legPitch();
        set(leftWing, pose.leftWing());
        set(leftWingTip, pose.leftTip());
        set(rightWing, pose.rightWing());
        set(rightWingTip, pose.rightTip());
    }

    private static void set(ModelPart part, float[] rotation) {
        part.pitch = rotation[0];
        part.yaw = rotation[1];
        part.roll = rotation[2];
    }
}
