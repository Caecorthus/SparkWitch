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
 * ({@code item/m32a1_body}) and the sight ({@code item/m32a1_sight}; together the full model without its objective
 * lens) at the one hip-to-aim blend of {@link M32SightPose}, then the reticle on its plane (pixel-style art v7, 2026-11-11: the
 * tube's open round bore is the window). Every matrix is computed before anything is drawn, and any missing model,
 * unusable matrix or missing base answers false, so the caller draws vanilla's full model instead (fail safe); p == 0
 * always answers false, so the vanilla call runs unchanged.
 * <p>
 * Matrix composition. The base {@code B} is captured at HEAD of the first-person
 * {@code HeldItemRenderer#renderItem(float, ...)} ({@link #beginHandPass}; vanilla's {@code renderHand} and Iris's
 * {@code HandRenderer} both call it, and it already carries hurt tilt and view bob). At vanilla's item draw the stack is
 * {@code S = B * hipPre} (lag sway, equip and swing offsets), and {@code ItemRenderer} would apply the display
 * transform {@code D} and {@code T(-0.5)}. Hip = {@code hipPre * D_full * T(-0.5)}; target = {@code blend(hip, aim)};
 * the stack is multiplied by {@code hipPre^-1 * target * T(0.5) * D_model^-1}, so {@code ItemRenderer}'s own display
 * transform lands each model exactly on {@code B * target}.
 * <p>
 * Reticle (owner 2026-11-11: no range dial, "let them shoot by the scale"; centre direct fire, scale below). It is a
 * handful of full-bright quads straight in the sight's vertex space, all from the one atlas {@link #RETICLE_TEXTURE}:
 * the centre mark ({@link M32SightPose#centreMark}: the screen centre is where a round leaves, aimed or from the hip),
 * and under it the stem with one tick and label per range mark of {@code M32Ballistics.scaleMarks()}. Each mark sits
 * where the round is at that range: {@code M32Ballistics.holdoverRadians} along the interpolated look, turned into the
 * hand projection with the live world FOV and this pass's hand FOV ({@link M32SightPose#scaleOffset}), so the scale
 * stays true at any pitch and FOV option; marks that do not fit the bore, or would crowd the mark above them at a
 * steep look, are not drawn. The whole reticle draws only
 * while its centre is inside the bore and fades in with {@link M32SightPose#reticleAlpha}. Presentation only; render
 * thread only; the server decides every shot.
 * <p>
 * Badge anchor (owner, 2026-11-06, option B): every main-hand draw also hands {@link M32BadgeAnchor} this frame's
 * screen anchor of the readout badge, p = 0 included ({@link #captureHipAnchor}: the hip pose vanilla is about to
 * draw), so {@link M32Hud} can ride the launcher; a declined aimed draw captures nothing and the HUD falls back.
 * 仅客户端：M2A1 瞄准进度 p 大于 0 时（{@link M32SightState}）的第一人称 M32A1。手中的发射器保持可见：不再进行原版的单次完整
 * 模型绘制（{@code item/m32a1}），而是按 {@link M32SightPose} 的腰射到瞄准过渡绘制机身（{@code item/m32a1_body}）与瞄具
 * （{@code item/m32a1_sight}；两者合起来即不含物镜的完整模型），两者同一姿态，最后绘制其平面上的分划（像素风美术 v7，2026-11-11：镜筒开放的
 * 圆形内孔即窗口）。所有矩阵都在绘制任何内容之前算好；缺少模型、矩阵不可用或没有基准时返回 false，调用方改为绘制原版完整模型
 * （失败安全）；p == 0 时总是返回 false，原版调用原样执行。
 * <p>
 * 矩阵组合。基准 {@code B} 在第一人称 {@code HeldItemRenderer#renderItem(float, ...)} 的 HEAD 捕获（{@link #beginHandPass}；
 * 原版 {@code renderHand} 与 Iris 的 {@code HandRenderer} 都调用它，且它已包含受伤倾斜与视角摇晃）。在原版物品绘制处矩阵栈为
 * {@code S = B * hipPre}（延迟摆动、装备与挥动偏移），随后 {@code ItemRenderer} 会应用显示变换 {@code D} 与 {@code T(-0.5)}。
 * 腰射 = {@code hipPre * D_full * T(-0.5)}；目标 = {@code blend(hip, aim)}；矩阵栈乘以
 * {@code hipPre^-1 * target * T(0.5) * D_model^-1}，使 {@code ItemRenderer} 自己的显示变换把每个模型精确放到 {@code B * target}。
 * <p>
 * 分划（所有者 2026-11-11：不设射程调节，“按刻度尺打”；中心直射、下方刻度）。它是直接位于瞄具顶点空间的若干全亮四边形，全部
 * 取自同一张图集 {@link #RETICLE_TEXTURE}：中心分划（{@link M32SightPose#centreMark}：无论开镜还是腰射，屏幕中心都是榴弹出膛
 * 方向），以及其下方的主干和 {@code M32Ballistics.scaleMarks()} 每个射程的一条刻度线与数字。每条刻度位于榴弹在该射程时所在的
 * 位置：沿插值视线的 {@code M32Ballistics.holdoverRadians}，再用实时世界视场角与本次手部视场角换算到手部投影
 * （{@link M32SightPose#scaleOffset}），因此刻度尺在任何俯仰角与视场角设置下都准确；放不进内孔、或在大俯仰角下会与上方刻度挤在一起的刻度不绘制。仅当分划中心位于
 * 内孔内时才绘制整个分划，并按 {@link M32SightPose#reticleAlpha} 淡入。仅为表现；仅渲染线程；每次发射都由服务端决定。
 * <p>
 * 标记锚点（所有者 2026-11-06，方案 B）：每次主手绘制也把本帧读数标记的屏幕锚点交给 {@link M32BadgeAnchor}，p = 0 时也一样
 * （{@link #captureHipAnchor}：原版即将绘制的腰射姿态），使 {@link M32Hud} 能跟随枪身；放弃的瞄准绘制不捕获任何内容，HUD 回退。
 */
public final class M32SightRenderer {
    public static final Identifier BODY_MODEL_ID = SparkWitch.id("item/m32a1_body");
    public static final Identifier SIGHT_MODEL_ID = SparkWitch.id("item/m32a1_sight");
    /**
     * The reticle atlas (128 x 128, colour baked, linear filtering): one solid red patch for every bar and the eight
     * range labels, laid out as {@link M32SightPose}'s {@code ATLAS_*} constants say.
     * 分划图集（128 x 128，颜色已烘焙，线性过滤）：供所有线条使用的一块纯红色块与八个射程数字，布局见
     * {@link M32SightPose} 的 {@code ATLAS_*} 常量。
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
        Matrix4f pose = M32SightPose.blend(hip, M32SightPose.aimPose(), M32SightPose.anchor(), ease);
        if (pose == null) {
            return false;
        }
        Matrix4f bodyCorrection = M32SightPose.drawCorrection(hipPre, pose, display(body, mode, leftHanded));
        Matrix4f sightCorrection = M32SightPose.drawCorrection(hipPre, pose, display(sight, mode, leftHanded));
        if (bodyCorrection == null || sightCorrection == null) {
            return false;
        }
        Matrix4f reticleCorrection = M32SightPose.directCorrection(hipPre, pose);
        // The hand projection and model-view now bound (vanilla renderHand / Iris HandRenderer), so the window test
        // sees exactly the picture the quads will make. / 当前绑定的手部投影与模型视图（原版 renderHand / Iris HandRenderer），
        // 因此窗口检测看到的正是这些四边形将呈现的画面。
        boolean reticle = reticleCorrection != null && M32SightPose.reticleInWindow(clipFromBase().mul(pose));
        // The badge anchor of this frame: both anchors under the one launcher pose, blended like it (M32BadgeAnchor).
        // 本帧标记锚点：两个锚点都经过同一个发射器姿态，并与其同样混合（M32BadgeAnchor）。
        Window window = client.getWindow();
        M32BadgeAnchor.capture(clipFromBase(), pose, pose, ease, window.getScaledWidth(), window.getScaledHeight());

        drawModel(renderer, stack, mode, leftHanded, matrices, consumers, light, overlay, body, bodyCorrection);
        drawModel(renderer, stack, mode, leftHanded, matrices, consumers, light, overlay, sight, sightCorrection);
        if (reticle) {
            // After the launcher in code order. The reticle layer tests depth but never writes it, and the opaque tube
            // does, so the tube covers it outside the window whichever layer the buffer flushes first.
            // 代码顺序上位于发射器之后。分划渲染层测试深度但从不写入，而不透明的镜筒会写入，因此无论缓冲先刷新哪一层，镜筒都会在
            // 窗口之外遮住它。
            drawReticle(matrices, consumers, reticleCorrection, M32SightPose.reticleAlpha(progress),
                    entity.getPitch(passTickDelta), M32SightState.handFovDegrees(), worldFov(client, passTickDelta));
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
     * The reticle on its plane with the launcher's matrix, full bright on the emissive translucent entity layer (depth
     * tested, never written), the atlas's own colour at {@code visibility}: the centre mark, then for each range mark
     * that fits the bore its tick and (when it fits too) its label, and last the stem down to the lowest tick drawn.
     * {@code pitch} is the look the round would leave along.
     * 以发射器的矩阵在分划平面上绘制分划，在自发光半透明实体渲染层上全亮绘制（测试深度、从不写入），图集自身颜色、透明度为
     * {@code visibility}：先是中心分划，然后对每个放得进内孔的射程刻度绘制刻度线及（同样放得下时）数字，最后是通到最低一条已绘制
     * 刻度线的主干。{@code pitch} 为榴弹将沿其射出的视线俯仰角。
     */
    private static void drawReticle(MatrixStack matrices, VertexConsumerProvider consumers, Matrix4f correction,
                                    float visibility, float pitch, double handFov, double worldFov) {
        if (!(visibility > 0.0F)) {
            return;
        }
        matrices.push();
        matrices.multiplyPositionMatrix(correction);
        MatrixStack.Entry entry = matrices.peek();
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(RETICLE_TEXTURE));
        int color = M32SightPose.reticleArgb(visibility);
        M32SightPose.Rect solid = M32SightPose.solidUv();
        for (M32SightPose.Rect bar : M32SightPose.centreMark()) {
            quad(buffer, entry, bar, solid, color);
        }
        double lowest = Double.NaN;
        int[] marks = M32Ballistics.scaleMarks();
        for (int i = 0; i < marks.length; i++) {
            double offset = M32SightPose.scaleOffset(M32Ballistics.holdoverRadians(pitch, marks[i]), handFov,
                    worldFov);
            if (!M32SightPose.tickFits(offset) || !M32SightPose.markClear(offset, lowest)) {
                continue;
            }
            quad(buffer, entry, M32SightPose.tickRect(offset), solid, color);
            int digits = marks[i] >= 100 ? 3 : 2;
            if (M32SightPose.labelFits(offset, digits)) {
                quad(buffer, entry, M32SightPose.labelRect(offset, digits), M32SightPose.labelUv(i, digits), color);
            }
            lowest = offset;
        }
        quad(buffer, entry, M32SightPose.stemRect(lowest), solid, color);
        matrices.pop();
    }

    /** One quad on the reticle plane. / 分划平面上的一个四边形。 */
    private static void quad(VertexConsumer buffer, MatrixStack.Entry entry, M32SightPose.Rect rect,
                             M32SightPose.Rect uv, int color) {
        float z = M32SightPose.RETICLE_Z / M32SightPose.UNITS_PER_BLOCK;
        // Corners top-left, bottom-left, bottom-right, top-right. / 角顺序为左上、左下、右下、右上。
        vertex(buffer, entry, rect.x0(), rect.y1(), z, uv.x0(), uv.y0(), color);
        vertex(buffer, entry, rect.x0(), rect.y0(), z, uv.x0(), uv.y1(), color);
        vertex(buffer, entry, rect.x1(), rect.y0(), z, uv.x1(), uv.y1(), color);
        vertex(buffer, entry, rect.x1(), rect.y1(), z, uv.x1(), uv.y0(), color);
    }

    private static void vertex(VertexConsumer buffer, MatrixStack.Entry entry, float x, float y, float z, float u,
                               float v, int color) {
        buffer.vertex(entry, x, y, z).color(color).texture(u, v).overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0.0F, 0.0F, 1.0F);
    }
}
