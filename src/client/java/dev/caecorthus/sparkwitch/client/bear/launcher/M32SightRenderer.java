package dev.caecorthus.sparkwitch.client.bear.launcher;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.mixin.scope.ScopeGameRendererInvoker;
import dev.caecorthus.sparkwitch.roles.civilian.bear.launcher.M32Ballistics;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Client only: the first-person M32A1 while the M2A1 aim progress p is above 0 ({@link M32SightState}). The held
 * launcher stays visible: instead of vanilla's single full-model draw ({@code item/m32a1}) it draws the body
 * ({@code item/m32a1_body}) at the hip-to-aim blend of {@link M32SightPose}, the sight ({@code item/m32a1_sight}) at
 * the same pose times the range tilt, then the dot. Every matrix is computed before anything is drawn, and any missing
 * model, unusable matrix or missing base answers false, so the caller draws vanilla's full model instead (fail safe);
 * p == 0 always answers false, so the vanilla call runs unchanged.
 * <p>
 * Matrix composition. The base {@code B} is captured at HEAD of the first-person
 * {@code HeldItemRenderer#renderItem(float, ...)} ({@link #beginHandPass}; vanilla's {@code renderHand} and Iris's
 * {@code HandRenderer} both call it, and it already carries hurt tilt and view bob). At vanilla's item draw the stack is
 * {@code S = B * hipPre} (lag sway, equip and swing offsets), and {@code ItemRenderer} would apply the display
 * transform {@code D} and {@code T(-0.5)}. Hip = {@code hipPre * D_full * T(-0.5)}; target = {@code blend(hip, aim)}
 * (body) or that times {@code sightTilt} (sight); the stack is multiplied by
 * {@code hipPre^-1 * target * T(0.5) * D_model^-1}, so {@code ItemRenderer}'s own display transform lands each model
 * exactly on {@code B * target}. The dot is two full-bright quads straight in the sight's vertex space.
 * <p>
 * Range tilt: h = {@link M32Ballistics#holdoverRadians} at the interpolated pitch and the dialled range (what the
 * retired 2-D reticle used), turned into the hand projection with the live world projection FOV and this pass's hand
 * FOV ({@link M32SightPose#tiltRadians}); it blends in with ease(p) so the sight never pops off the hip pose. No aim
 * solution (out of range before the fuse) leaves the sight untilted and draws no dot. Presentation only; render thread
 * only; the server decides every shot.
 * 仅客户端：M2A1 瞄准进度 p 大于 0 时（{@link M32SightState}）的第一人称 M32A1。手中的发射器保持可见：不再进行原版的单次完整
 * 模型绘制（{@code item/m32a1}），而是按 {@link M32SightPose} 的腰射到瞄准过渡绘制机身（{@code item/m32a1_body}），在同一姿态再乘
 * 以射程俯仰绘制瞄具（{@code item/m32a1_sight}），最后绘制红点。所有矩阵都在绘制任何内容之前算好；缺少模型、矩阵不可用或没有
 * 基准时返回 false，调用方改为绘制原版完整模型（失败安全）；p == 0 时总是返回 false，原版调用原样执行。
 * <p>
 * 矩阵组合。基准 {@code B} 在第一人称 {@code HeldItemRenderer#renderItem(float, ...)} 的 HEAD 捕获（{@link #beginHandPass}；
 * 原版 {@code renderHand} 与 Iris 的 {@code HandRenderer} 都调用它，且它已包含受伤倾斜与视角摇晃）。在原版物品绘制处矩阵栈为
 * {@code S = B * hipPre}（延迟摆动、装备与挥动偏移），随后 {@code ItemRenderer} 会应用显示变换 {@code D} 与 {@code T(-0.5)}。
 * 腰射 = {@code hipPre * D_full * T(-0.5)}；目标 = {@code blend(hip, aim)}（机身）或再乘以 {@code sightTilt}（瞄具）；矩阵栈乘以
 * {@code hipPre^-1 * target * T(0.5) * D_model^-1}，使 {@code ItemRenderer} 自己的显示变换把每个模型精确放到 {@code B * target}。
 * 红点是直接位于瞄具顶点空间的两个全亮四边形。
 * <p>
 * 射程俯仰：h = 插值俯仰角与当前刻度下的 {@link M32Ballistics#holdoverRadians}（即已退役的 2D 分划所用的值），再用实时世界投影
 * 视场角与本次手部视场角换算到手部投影（{@link M32SightPose#tiltRadians}）；它随 ease(p) 淡入，瞄具不会从腰射姿态跳变。没有
 * 瞄准解（引信前飞不到该距离）时瞄具不俯仰且不画红点。仅为表现；仅渲染线程；每次发射都由服务端决定。
 */
public final class M32SightRenderer {
    public static final Identifier BODY_MODEL_ID = SparkWitch.id("item/m32a1_body");
    public static final Identifier SIGHT_MODEL_ID = SparkWitch.id("item/m32a1_sight");
    /** Left half: the soft red halo; right half: the crisp white core. / 左半为柔和红色光晕，右半为清晰白色核心。 */
    public static final Identifier DOT_TEXTURE = SparkWitch.id("textures/entity/bear/m2a1_dot.png");
    @Nullable
    private static MatrixStack passStack;
    private static final Matrix4f PASS_BASE = new Matrix4f();
    private static float passTickDelta;

    private M32SightRenderer() {
    }

    /**
     * HEAD of the first-person {@code HeldItemRenderer#renderItem(float, ...)}: remembers this hand pass's stack, its
     * base matrix and tick delta. / 第一人称 {@code HeldItemRenderer#renderItem(float, ...)} 的 HEAD：记住本次手部渲染的矩阵栈、
     * 基准矩阵与 tick delta。
     */
    public static void beginHandPass(MatrixStack matrices, float tickDelta) {
        passStack = matrices;
        PASS_BASE.set(matrices.peek().getPositionMatrix());
        passTickDelta = tickDelta;
    }

    /** TAIL of the same method: the base is no longer valid. / 同一方法的 TAIL：基准不再有效。 */
    public static void endHandPass() {
        passStack = null;
    }

    /**
     * Vanilla's {@code ItemRenderer#renderItem(LivingEntity, ...)} call inside {@code HeldItemRenderer#renderItem
     * (LivingEntity, ...)} for the renderer's main-hand stack: draws the aimed launcher and returns true, or returns
     * false (nothing drawn) so the caller makes the vanilla call.
     * {@code HeldItemRenderer#renderItem(LivingEntity, ...)} 中对渲染器主手物品的原版 {@code ItemRenderer#renderItem(LivingEntity, ...)}
     * 调用：绘制瞄准中的发射器并返回 true；或返回 false（未绘制任何内容），由调用方执行原版调用。
     */
    public static boolean draw(ItemRenderer renderer, LivingEntity entity, ItemStack stack, ModelTransformationMode mode,
                               boolean leftHanded, MatrixStack matrices, VertexConsumerProvider consumers, World world,
                               int light, int overlay, int seed) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (passStack != matrices || !mode.isFirstPerson() || entity != client.player
                || !M32ClientState.isLauncher(stack)) {
            return false;
        }
        float progress = M32SightState.progress();
        if (!(progress > 0.0F)) {
            return false;
        }
        BakedModel body = extraModel(BODY_MODEL_ID);
        BakedModel sight = extraModel(SIGHT_MODEL_ID);
        if (body == null || sight == null) {
            return false;
        }
        Matrix4f inverseBase = M32SightPose.invert(PASS_BASE);
        if (inverseBase == null) {
            return false;
        }
        Matrix4f hipPre = inverseBase.mul(matrices.peek().getPositionMatrix());
        BakedModel full = renderer.getModel(stack, world, entity, seed);
        Matrix4f hip = M32SightPose.hipPose(hipPre, display(full, mode, leftHanded));
        float ease = M32SightPose.ease(progress);
        double solvedTilt = M32SightPose.tiltRadians(
                M32Ballistics.holdoverRadians(entity.getPitch(passTickDelta), M32RangeDial.metres()),
                M32SightState.handFovDegrees(), worldFov(client, passTickDelta));
        boolean solved = Double.isFinite(solvedTilt);
        float tilt = solved ? (float) solvedTilt * ease : 0.0F;
        Matrix4f bodyPose = M32SightPose.blend(hip, M32SightPose.aimPose(tilt), M32SightPose.anchor(tilt), ease);
        if (bodyPose == null) {
            return false;
        }
        Matrix4f sightPose = new Matrix4f(bodyPose).mul(M32SightPose.sightTilt(tilt));
        Matrix4f bodyCorrection = M32SightPose.drawCorrection(hipPre, bodyPose, display(body, mode, leftHanded));
        Matrix4f sightCorrection = M32SightPose.drawCorrection(hipPre, sightPose, display(sight, mode, leftHanded));
        if (bodyCorrection == null || sightCorrection == null) {
            return false;
        }
        Matrix4f dotCorrection = solved ? M32SightPose.directCorrection(hipPre, sightPose) : null;
        // The hand projection and model-view now bound (vanilla renderHand / Iris HandRenderer), so the window test
        // sees exactly the picture the quads will make. / 当前绑定的手部投影与模型视图（原版 renderHand / Iris HandRenderer），
        // 因此窗口检测看到的正是这些四边形将呈现的画面。
        boolean dot = dotCorrection != null && M32SightPose.dotInWindow(new Matrix4f(RenderSystem.getProjectionMatrix())
                .mul(RenderSystem.getModelViewMatrix()).mul(PASS_BASE).mul(sightPose));

        drawModel(renderer, stack, mode, leftHanded, matrices, consumers, light, overlay, body, bodyCorrection);
        drawModel(renderer, stack, mode, leftHanded, matrices, consumers, light, overlay, sight, sightCorrection);
        if (dot) {
            // After the launcher in code order. The dot layer tests depth but never writes it, and the opaque frame does,
            // so the frame covers the dot outside the window whichever layer the buffer flushes first.
            // 代码顺序上位于发射器之后。红点渲染层测试深度但从不写入，而不透明的框体会写入，因此无论缓冲先刷新哪一层，框体都会在
            // 窗口之外遮住红点。
            drawDot(matrices, consumers, dotCorrection);
        }
        return true;
    }

    /** The extra model, or null when it is missing or failed to bake. / 额外模型；缺失或烘焙失败时为 null。 */
    @Nullable
    private static BakedModel extraModel(Identifier id) {
        BakedModelManager manager = MinecraftClient.getInstance().getBakedModelManager();
        BakedModel model = ((FabricBakedModelManager) manager).getModel(id);
        return model == null || model == manager.getMissingModel() ? null : model;
    }

    /** The model's display transform for {@code mode}, as {@code ItemRenderer} applies it. / 模型在该模式下的显示变换。 */
    private static Matrix4f display(BakedModel model, ModelTransformationMode mode, boolean leftHanded) {
        MatrixStack transform = new MatrixStack();
        model.getTransformation().getTransformation(mode).apply(leftHanded, transform);
        return new Matrix4f(transform.peek().getPositionMatrix());
    }

    /** The live world projection FOV (options x zoom, death, submersion, other mods). / 实时世界投影视场角。 */
    private static double worldFov(MinecraftClient client, float tickDelta) {
        return ((ScopeGameRendererInvoker) client.gameRenderer)
                .sparkwitch$scopeFov(client.gameRenderer.getCamera(), tickDelta, true);
    }

    private static void drawModel(ItemRenderer renderer, ItemStack stack, ModelTransformationMode mode,
                                  boolean leftHanded, MatrixStack matrices, VertexConsumerProvider consumers, int light,
                                  int overlay, BakedModel model, Matrix4f correction) {
        matrices.push();
        matrices.multiplyPositionMatrix(correction);
        renderer.renderItem(stack, mode, leftHanded, matrices, consumers, light, overlay, model);
        matrices.pop();
    }

    /**
     * The red halo, then the white core slightly nearer the eye, perpendicular to the sight axis at
     * {@link M32SightPose#DOT_Z}; full bright on the emissive translucent entity layer.
     * 先画红色光晕，再画离眼睛稍近的白色核心，均垂直于瞄具轴、位于 {@link M32SightPose#DOT_Z}；在自发光半透明实体渲染层上全亮绘制。
     */
    private static void drawDot(MatrixStack matrices, VertexConsumerProvider consumers, Matrix4f correction) {
        matrices.push();
        matrices.multiplyPositionMatrix(correction);
        MatrixStack.Entry entry = matrices.peek();
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(DOT_TEXTURE));
        Vector3f center = M32SightPose.dotCenter();
        float perUnit = 1.0F / M32SightPose.UNITS_PER_BLOCK;
        quad(buffer, entry, center.x, center.y, center.z, M32SightPose.HALO_EDGE * perUnit / 2.0F, 0.0F, 0.5F);
        quad(buffer, entry, center.x, center.y, center.z + M32SightPose.CORE_LEAD * perUnit,
                M32SightPose.CORE_EDGE * perUnit / 2.0F, 0.5F, 1.0F);
        matrices.pop();
    }

    private static void quad(VertexConsumer buffer, MatrixStack.Entry entry, float x, float y, float z, float half,
                             float u0, float u1) {
        vertex(buffer, entry, x - half, y + half, z, u0, 0.0F);
        vertex(buffer, entry, x - half, y - half, z, u0, 1.0F);
        vertex(buffer, entry, x + half, y - half, z, u1, 1.0F);
        vertex(buffer, entry, x + half, y + half, z, u1, 0.0F);
    }

    private static void vertex(VertexConsumer buffer, MatrixStack.Entry entry, float x, float y, float z, float u,
                               float v) {
        buffer.vertex(entry, x, y, z).color(0xFFFFFFFF).texture(u, v).overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0.0F, 0.0F, 1.0F);
    }
}
