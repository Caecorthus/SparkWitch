package dev.caecorthus.sparkwitch.client.seeker.render;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.seeker.SeekerClientState;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCameraEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

/**
 * Client only: renders the baked wall-camera model oriented by the synced FACING and MOUNT_YAW. The model is authored
 * as a wall camera whose mount plate sits against a wall at model south (+Z, 0.15 from the cube centre) and whose
 * lens looks north (-Z); it is rotated so the plate lies on the mount face and floor/ceiling cameras turn toward
 * MOUNT_YAW. The local owner does not see the camera it is currently viewing through.
 * 仅客户端：按同步的 FACING 与 MOUNT_YAW 渲染烘焙的墙面摄像头模型。模型以墙面摄像头为基准：安装板贴在模型南侧（+Z，
 * 距立方体中心 0.15）的墙上，镜头朝北（-Z）；渲染时旋转使安装板贴合依附面，地面与天花板摄像头转向 MOUNT_YAW。
 * 本地拥有者正在通过该摄像头观看时不渲染它。
 */
public class SeekerCameraEntityRenderer extends EntityRenderer<SeekerCameraEntity> {
    private final ItemRenderer itemRenderer;
    private final ItemStack stack;

    public SeekerCameraEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
        stack = new ItemStack(SparkWitchItems.seekerCamera());
    }

    @Override
    public void render(SeekerCameraEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        if (!isViewedLocally(entity)) {
            matrices.push();
            // Rotate about the cube centre. / 绕立方体中心旋转。
            matrices.translate(0.0, SeekerRules.CAMERA_SIZE / 2.0, 0.0);
            orient(matrices, entity.facing(), entity.mountYaw());
            itemRenderer.renderItem(stack, ModelTransformationMode.FIXED, false, matrices, vertexConsumers, light,
                    OverlayTexture.DEFAULT_UV, SeekerModels.model(SeekerRules.CAMERA_PLACED_MODEL_ID));
            matrices.pop();
        }
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    /**
     * Maps the model's outward normal (-Z) onto {@code facing}. Walls: yaw only. Floor: -Z → +Y, then the lens tilt
     * (model -Y) turns toward MOUNT_YAW. Ceiling: -Z → -Y, same horizontal centre.
     * 把模型的外法线（-Z）映射到依附面法线。墙面：只转偏航。地面：-Z → +Y，镜头倾斜方向转向 MOUNT_YAW。
     * 天花板：-Z → -Y，水平中心相同。
     */
    private static void orient(MatrixStack matrices, Direction facing, float mountYaw) {
        switch (facing) {
            case UP -> {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - mountYaw));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
            }
            case DOWN -> {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mountYaw));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0F));
            }
            default -> matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - facing.asRotation()));
        }
    }

    private static boolean isViewedLocally(SeekerCameraEntity entity) {
        return SeekerClientState.sessionMode() == SeekerSessionMode.CAMERA
                && SeekerClientState.cameraEntityId() == entity.getId();
    }

    @Override
    public Identifier getTexture(SeekerCameraEntity entity) {
        return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
    }
}
