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
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Client only: the first-person M32A1 while the M2A1 aim progress p is above 0 ({@link M32SightState}). The held
 * launcher stays visible: instead of vanilla's single full-model draw ({@code item/m32a1}) it draws the body
 * ({@code item/m32a1_body}) at the hip-to-aim blend of {@link M32SightPose}, the sight ({@code item/m32a1_sight}) at
 * the same pose times the range tilt, then the reticle on its plane (pixel-style art v7, 2026-11-11: the tube's open round bore is
 * the window). Every matrix is computed before anything is drawn, and any missing
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
 * exactly on {@code B * target}. The reticle ({@link M32SightPose#reticleQuad}) is one full-bright quad straight in the
 * sight's vertex space; it draws only while its centre is inside the bore and fades in with
 * {@link M32SightPose#reticleAlpha}.
 * <p>
 * Range tilt: h = {@link M32Ballistics#elevationToHitRadians} along the interpolated look at the dialled range (the
 * angle {@link M32DialRules#launchPitch} raises an aimed shot by, so the screen centre is where it lands), turned into
 * the hand projection with the live world projection FOV and this pass's hand
 * FOV ({@link M32SightPose#tiltRadians}); it blends in with ease(p) so the sight never pops off the hip pose. No aim
 * solution (out of range before the fuse) leaves the sight untilted and draws no reticle. Presentation only;
 * render thread only; the server decides every shot.
 * <p>
 * Badge anchor (owner, 2026-11-06, option B): every main-hand draw also hands {@link M32BadgeAnchor} this frame's
 * screen anchor of the readout badge, p = 0 included ({@link #captureHipAnchor}: the hip pose vanilla is about to
 * draw), so {@link M32Hud} can ride the launcher; a declined aimed draw captures nothing and the HUD falls back.
 * 仅客户端：M2A1 瞄准进度 p 大于 0 时（{@link M32SightState}）的第一人称 M32A1。手中的发射器保持可见：不再进行原版的单次完整
 * 模型绘制（{@code item/m32a1}），而是按 {@link M32SightPose} 的腰射到瞄准过渡绘制机身（{@code item/m32a1_body}），在同一姿态再乘
 * 以射程俯仰绘制瞄具（{@code item/m32a1_sight}），最后绘制其平面上的分划（像素风美术 v7，2026-11-11：镜筒开放的圆形
 * 内孔即窗口）。所有矩阵都在绘制任何内容之前算好；缺少模型、矩阵不可用或没有基准时返回 false，调用方改为绘制原版完整模型
 * （失败安全）；p == 0 时总是返回 false，原版调用原样执行。
 * <p>
 * 矩阵组合。基准 {@code B} 在第一人称 {@code HeldItemRenderer#renderItem(float, ...)} 的 HEAD 捕获（{@link #beginHandPass}；
 * 原版 {@code renderHand} 与 Iris 的 {@code HandRenderer} 都调用它，且它已包含受伤倾斜与视角摇晃）。在原版物品绘制处矩阵栈为
 * {@code S = B * hipPre}（延迟摆动、装备与挥动偏移），随后 {@code ItemRenderer} 会应用显示变换 {@code D} 与 {@code T(-0.5)}。
 * 腰射 = {@code hipPre * D_full * T(-0.5)}；目标 = {@code blend(hip, aim)}（机身）或再乘以 {@code sightTilt}（瞄具）；矩阵栈乘以
 * {@code hipPre^-1 * target * T(0.5) * D_model^-1}，使 {@code ItemRenderer} 自己的显示变换把每个模型精确放到 {@code B * target}。
 * 分划（{@link M32SightPose#reticleQuad}）是直接位于瞄具顶点空间的一个全亮四边形；仅当其中心位于内孔内时绘制，并按
 * {@link M32SightPose#reticleAlpha} 淡入。
 * <p>
 * 射程俯仰：h = 沿插值视线、当前刻度下的 {@link M32Ballistics#elevationToHitRadians}（即 {@link M32DialRules#launchPitch} 为开镜
 * 射击抬高的角度，因此屏幕中心就是落点），再用实时世界投影
 * 视场角与本次手部视场角换算到手部投影（{@link M32SightPose#tiltRadians}）；它随 ease(p) 淡入，瞄具不会从腰射姿态跳变。没有
 * 瞄准解（引信前飞不到该距离）时瞄具不俯仰，也不画分划。仅为表现；仅渲染线程；每次发射都由服务端决定。
 * <p>
 * 标记锚点（所有者 2026-11-06，方案 B）：每次主手绘制也把本帧读数标记的屏幕锚点交给 {@link M32BadgeAnchor}，p = 0 时也一样
 * （{@link #captureHipAnchor}：原版即将绘制的腰射姿态），使 {@link M32Hud} 能跟随枪身；放弃的瞄准绘制不捕获任何内容，HUD 回退。
 */
public final class M32SightRenderer {
    public static final Identifier BODY_MODEL_ID = SparkWitch.id("item/m32a1_body");
    public static final Identifier SIGHT_MODEL_ID = SparkWitch.id("item/m32a1_sight");
    /**
     * The reticle (128 x 128, colour baked, linear filtering): four double-barred red arms round an empty centre.
     * 分划贴图（128 x 128，颜色已烘焙，线性过滤）：围绕空心中心的四条双横杠红色短臂。
     */
    public static final Identifier RETICLE_TEXTURE = SparkWitch.id("textures/entity/bear/m2a1_reticle.png");
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
            captureHipAnchor(renderer, entity, stack, mode, leftHanded, matrices, world, seed);
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
        float pitch = entity.getPitch(passTickDelta);
        int dial = M32RangeDial.metres();
        double handFov = M32SightState.handFovDegrees();
        double worldFov = worldFov(client, passTickDelta);
        double solvedTilt = M32SightPose.tiltRadians(M32Ballistics.elevationToHitRadians(pitch, dial), handFov,
                worldFov);
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
        Matrix4f reticleCorrection = solved ? M32SightPose.directCorrection(hipPre, sightPose) : null;
        // The hand projection and model-view now bound (vanilla renderHand / Iris HandRenderer), so the window test
        // sees exactly the picture the quads will make. / 当前绑定的手部投影与模型视图（原版 renderHand / Iris HandRenderer），
        // 因此窗口检测看到的正是这些四边形将呈现的画面。
        boolean reticle = reticleCorrection != null
                && M32SightPose.reticleInWindow(clipFromBase().mul(sightPose));
        // The badge anchor of this frame: the hip anchor under the body pose, the aim anchor under the tilted sight
        // pose, blended like the poses (M32BadgeAnchor). / 本帧标记锚点：机身姿态下的腰射锚点、俯仰后瞄具姿态下的瞄准锚点，
        // 与姿态同样混合（M32BadgeAnchor）。
        Window window = client.getWindow();
        M32BadgeAnchor.capture(clipFromBase(), bodyPose, sightPose, ease, window.getScaledWidth(),
                window.getScaledHeight());

        drawModel(renderer, stack, mode, leftHanded, matrices, consumers, light, overlay, body, bodyCorrection);
        drawModel(renderer, stack, mode, leftHanded, matrices, consumers, light, overlay, sight, sightCorrection);
        if (reticle) {
            // After the launcher in code order. The reticle layer tests depth but never writes it, and the opaque tube
            // does, so the tube covers it outside the window whichever layer the buffer flushes first.
            // 代码顺序上位于发射器之后。分划渲染层测试深度但从不写入，而不透明的镜筒会写入，因此无论缓冲先刷新哪一层，镜筒都会在
            // 窗口之外遮住它。
            drawReticle(matrices, consumers, reticleCorrection, M32SightPose.reticleAlpha(progress));
        }
        return true;
    }

    /**
     * p = 0: vanilla is about to draw the full model at the hip, so the badge anchor is the rail top under that very
     * pose ({@code hipPre * D_full * T(-0.5)}), blend 0. A singular base leaves no anchor (the HUD falls back).
     * p = 0：原版即将在腰射姿态绘制完整模型，因此标记锚点就是该姿态（{@code hipPre * D_full * T(-0.5)}）下的导轨顶，混合为 0。
     * 基准奇异时不产生锚点（HUD 回退）。
     */
    private static void captureHipAnchor(ItemRenderer renderer, LivingEntity entity, ItemStack stack,
                                         ModelTransformationMode mode, boolean leftHanded, MatrixStack matrices,
                                         World world, int seed) {
        Matrix4f inverseBase = M32SightPose.invert(PASS_BASE);
        if (inverseBase == null) {
            return;
        }
        Matrix4f hipPre = inverseBase.mul(matrices.peek().getPositionMatrix());
        BakedModel full = renderer.getModel(stack, world, entity, seed);
        Matrix4f hip = M32SightPose.hipPose(hipPre, display(full, mode, leftHanded));
        Window window = MinecraftClient.getInstance().getWindow();
        M32BadgeAnchor.capture(clipFromBase(), hip, hip, 0.0F, window.getScaledWidth(), window.getScaledHeight());
    }

    /**
     * Base space to clip space of this hand pass: the bound hand projection and model-view (vanilla {@code renderHand}
     * loads {@code getBasicProjectionMatrix(getFov(camera, tickDelta, false))} and pushes the camera rotation that the
     * base inverts; Iris's {@code HandRenderer} binds the same) times the base, so a vertex-space point through a pose
     * and this matrix is exactly the picture the quads make. Fresh copy.
     * 本次手部渲染从基准空间到裁剪空间的矩阵：当前绑定的手部投影与模型视图（原版 {@code renderHand} 加载
     * {@code getBasicProjectionMatrix(getFov(camera, tickDelta, false))} 并压入基准所抵消的相机旋转；Iris 的 {@code HandRenderer}
     * 绑定相同内容）乘以基准，因此顶点空间的点经姿态再经此矩阵得到的正是四边形呈现的画面。返回新副本。
     */
    private static Matrix4f clipFromBase() {
        return new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(PASS_BASE);
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
     * The reticle quad on its plane with the sight's matrix, full bright on the emissive translucent entity layer (depth
     * tested, never written), the texture's own colour at {@code visibility}.
     * 以瞄具的矩阵在分划平面上绘制分划四边形，在自发光半透明实体渲染层上全亮绘制（测试深度、从不写入），贴图自身颜色、透明度为
     * {@code visibility}。
     */
    private static void drawReticle(MatrixStack matrices, VertexConsumerProvider consumers, Matrix4f correction,
                                    float visibility) {
        if (!(visibility > 0.0F)) {
            return;
        }
        matrices.push();
        matrices.multiplyPositionMatrix(correction);
        MatrixStack.Entry entry = matrices.peek();
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(RETICLE_TEXTURE));
        float z = M32SightPose.RETICLE_Z / M32SightPose.UNITS_PER_BLOCK;
        M32SightPose.ReticleQuad quad = M32SightPose.reticleQuad();
        int color = M32SightPose.reticleArgb(visibility);
        // Corners top-left, bottom-left, bottom-right, top-right. / 角顺序为左上、左下、右下、右上。
        vertex(buffer, entry, quad.x0(), quad.y1(), z, 0.0F, 0.0F, color);
        vertex(buffer, entry, quad.x0(), quad.y0(), z, 0.0F, 1.0F, color);
        vertex(buffer, entry, quad.x1(), quad.y0(), z, 1.0F, 1.0F, color);
        vertex(buffer, entry, quad.x1(), quad.y1(), z, 1.0F, 0.0F, color);
        matrices.pop();
    }

    private static void vertex(VertexConsumer buffer, MatrixStack.Entry entry, float x, float y, float z, float u,
                               float v, int color) {
        buffer.vertex(entry, x, y, z).color(color).texture(u, v).overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0.0F, 0.0F, 1.0F);
    }
}
