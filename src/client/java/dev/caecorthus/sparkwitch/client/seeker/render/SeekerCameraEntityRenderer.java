package dev.caecorthus.sparkwitch.client.seeker.render;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.seeker.SeekerClientState;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCameraEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Client only: renders the placed camera in three baked parts. The static mount (plate and arm) is oriented by the
 * synced FACING and MOUNT_YAW: it is authored as a wall camera whose plate sits against a wall at model south (+Z, 0.15
 * from the cube centre), rotated so the plate lies on the mount face and floor/ceiling mounts turn toward MOUNT_YAW. The
 * head (housing, visor, lens, lens looking model north) pivots at its centre on the arm and points in world space
 * along the synced look, smoothed per tick and lerped per frame, so every player sees it follow the Seeker's view. The
 * LED rides on the head: full-bright red while the camera is VIEWING, dim otherwise. The local owner does not see the
 * camera it is currently viewing through.
 * 仅客户端：分三个烘焙部件渲染放置的摄像头。静态底座（安装板与支臂）按同步的 FACING 与 MOUNT_YAW 定向：它以墙面摄像头为基准，
 * 安装板贴在模型南侧（+Z，距立方体中心 0.15）的墙上，渲染时旋转使安装板贴合依附面，地面与天花板底座转向 MOUNT_YAW。
 * 机头（外壳、遮光罩、镜头，镜头朝模型北侧）以其位于支臂上的中心为轴，在世界空间中沿同步视角指向（逐刻平滑、逐帧插值），
 * 因此所有玩家都能看到它跟随搜寻者的视角转动。指示灯随机头一起转动：摄像头处于 VIEWING 时全亮红色，否则变暗。
 * 本地拥有者正在通过该摄像头观看时不渲染它。
 */
public class SeekerCameraEntityRenderer extends EntityRenderer<SeekerCameraEntity> {
    /**
     * Head pivot at the housing centre, model (8, 8.125, 7.5), where the arm ends inside the housing; relative to the
     * model centre in blocks. Turning about the middle keeps the head off the plate and wall at the cone edges.
     * 机头转轴位于外壳中心（模型坐标 (8, 8.125, 7.5)），支臂在外壳内部止于此处；以方块为单位、相对模型中心。
     * 绕中心转动可使机头在锥角边缘也不会穿进安装板与墙面。
     */
    private static final Vector3f HEAD_PIVOT = new Vector3f(0.0F, 0.125F / 16.0F, -0.5F / 16.0F);
    /** LED colour multiplier while nobody views the camera. / 无人观看时指示灯的颜色系数。 */
    private static final float LED_DIM = 0.3F;
    private static final Direction[] QUAD_SIDES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST, null};
    private final ItemRenderer itemRenderer;
    private final ItemStack stack;
    private final Random random = Random.create();

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
            Quaternionf mount = mountRotation(entity.facing(), entity.mountYaw());
            matrices.push();
            matrices.multiply(mount);
            renderPart(matrices, vertexConsumers, light, SeekerModels.model(SeekerRules.CAMERA_PLACED_MODEL_ID));
            matrices.pop();

            // The pivot follows the mount; the head's rotation is absolute (world yaw/pitch), not mount-relative.
            // 转轴随底座移动；机头的旋转是绝对的（世界偏航/俯仰），而非相对底座。
            Vector3f pivot = mount.transform(new Vector3f(HEAD_PIVOT));
            matrices.translate(pivot.x, pivot.y, pivot.z);
            orientHead(matrices, entity.renderLookYaw(tickDelta), entity.renderLookPitch(tickDelta));
            matrices.translate(-HEAD_PIVOT.x, -HEAD_PIVOT.y, -HEAD_PIVOT.z);
            renderPart(matrices, vertexConsumers, light, SeekerModels.model(SeekerRules.CAMERA_HEAD_MODEL_ID));
            renderLed(matrices, vertexConsumers, light, entity.isViewed());
            matrices.pop();
        }
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    private void renderPart(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, BakedModel model) {
        itemRenderer.renderItem(stack, ModelTransformationMode.FIXED, false, matrices, vertexConsumers, light,
                OverlayTexture.DEFAULT_UV, model);
    }

    /**
     * Draws the LED quads with a colour multiplier: full-bright while VIEWING, dimmed under world light otherwise.
     * The item renderer's centring translation is repeated so the LED lines up with the head model.
     * 用颜色系数绘制指示灯四边形：VIEWING 时全亮，否则在世界光照下变暗。这里重复物品渲染器的居中平移，使指示灯与机头模型对齐。
     */
    private void renderLed(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, boolean viewed) {
        BakedModel model = SeekerModels.model(SeekerRules.CAMERA_LED_MODEL_ID);
        float shade = viewed ? 1.0F : LED_DIM;
        int ledLight = viewed ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
        VertexConsumer consumer = vertexConsumers.getBuffer(TexturedRenderLayers.getEntityCutout());
        matrices.push();
        matrices.translate(-0.5F, -0.5F, -0.5F);
        MatrixStack.Entry entry = matrices.peek();
        for (Direction side : QUAD_SIDES) {
            random.setSeed(42L);
            for (BakedQuad quad : model.getQuads(null, side, random)) {
                consumer.quad(entry, quad, shade, shade, shade, 1.0F, ledLight, OverlayTexture.DEFAULT_UV);
            }
        }
        matrices.pop();
    }

    /**
     * Mount rotation: maps the model's outward normal (-Z) onto {@code facing}. Walls: yaw only. Floor: -Z → +Y, then
     * model -Y turns toward MOUNT_YAW. Ceiling: -Z → -Y, same horizontal centre.
     * 底座旋转：把模型的外法线（-Z）映射到依附面法线。墙面：只转偏航。地面：-Z → +Y，模型 -Y 方向转向 MOUNT_YAW。
     * 天花板：-Z → -Y，水平中心相同。
     */
    private static Quaternionf mountRotation(Direction facing, float mountYaw) {
        return switch (facing) {
            case UP -> RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - mountYaw)
                    .mul(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
            case DOWN -> RotationAxis.POSITIVE_Y.rotationDegrees(-mountYaw)
                    .mul(RotationAxis.POSITIVE_X.rotationDegrees(-90.0F));
            default -> RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - facing.asRotation());
        };
    }

    /**
     * Turns the head's lens (model -Z) to the Minecraft look (yaw 0 = south, positive pitch = down), as the car
     * renderer turns its front.
     * 把机头镜头（模型 -Z）转向 Minecraft 视角（yaw 0 朝南，pitch 为正朝下），与小车渲染器转动车头的方式一致。
     */
    private static void orientHead(MatrixStack matrices, float lookYaw, float lookPitch) {
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - lookYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-lookPitch));
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
