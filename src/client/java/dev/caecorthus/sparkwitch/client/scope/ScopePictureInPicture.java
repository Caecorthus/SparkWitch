package dev.caecorthus.sparkwitch.client.scope;

import com.mojang.blaze3d.platform.GlDebugInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.mixin.PostEffectProcessorAccessor;
import dev.caecorthus.sparkwitch.client.mixin.scope.ScopeGameRendererInvoker;
import dev.caecorthus.sparkwitch.client.mixin.scope.ScopeMinecraftClientAccessor;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.JsonEffectShaderProgram;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;

import java.io.IOException;

/**
 * Client only. The PICTURE_IN_PICTURE lens (WP4b): the main view stays at 1x, sharp and unblurred, and only the lens
 * circle shows a second, magnified world render.
 * <p>
 * Once per scoped frame, right before the GUI ({@code ScopeRuntime.beforeGui}, after the main world, the hand pass, the
 * entity-outline composite and the vanilla post pass), {@link #render} calls {@code WorldRenderer#render} again with:
 * <ul>
 *   <li>the same {@link Camera} and the main pass's frustum: {@code setupFrustum} is not called again, so vanilla's
 *   visible-section list (re-filtered only when the pitch/yaw bucket changes) and Sodium's render lists (rebuilt only
 *   when the camera moves or turns) are reused, and no section is rebuilt or re-sorted twice. The lens is a narrower
 *   cone around the same axis, so the 1x frustum always contains it;</li>
 *   <li>the main projection captured at {@code render} HEAD, narrowed in clip space by
 *   {@link ScopeRules#lensClipScale} (the same picture ZOOM_BLUR shows, bobbing and recoil included);</li>
 *   <li>{@code MinecraftClient#getFramebuffer()} pointed at {@link #LENS_VIEW_TARGET}, a square target of
 *   {@link ScopeRules#lensViewSize} (at most 512²), because vanilla re-binds that framebuffer mid-pass;</li>
 *   <li>no block outline, clouds, weather, particles or hands. Glowing/instinct outlines of the pass are composited
 *   onto the lens like vanilla does on the main view, then the in-wall, underwater and fire overlays are drawn over it,
 *   so a scoped player inside a block cannot see through it.</li>
 * </ul>
 * Saved and restored around the pass: the framebuffer field, the projection matrix, the model-view stack, the
 * viewport and the bound framebuffer. {@code WorldRenderer#render} itself resets fog ({@code clearFog}), blending and
 * the depth mask. The {@code scope_pip.json} composite then draws the lens over the main view with the same lens look
 * as ZOOM_BLUR (distortion, edge darkening, tint, smudges, streak, scope shadow) and a short dark tube rim.
 * <p>
 * Lifecycle: built on first use, rebuilt on resize, released 2 s after leaving PiP and on disconnect or resource
 * reload. Any failure closes it and backs off ({@link ScopeRules#retryDelaySeconds}); meanwhile {@link #ready()} is
 * false and the scope renders as ZOOM_BLUR. Fabulous! graphics and Iris shader packs never reach this class
 * ({@link ScopeRules#effectiveMode}). Render thread only; presentation only.
 * 仅客户端。画中画镜片（WP4b）：主画面保持清晰、不模糊的 1 倍，只有镜片圆内显示第二次放大的世界渲染。
 * <p>
 * 每个开镜帧在 GUI 之前（{@code ScopeRuntime.beforeGui}，位于主世界、手部、实体描边合成与原版后处理之后）由 {@link #render}
 * 再次调用 {@code WorldRenderer#render}：使用同一个 {@link Camera} 与主渲染的视锥——不再调用 {@code setupFrustum}，因此原版的
 * 可见区段列表（仅在俯仰/偏航分桶变化时重新筛选）与 Sodium 的渲染列表（仅在相机移动或转动时重建）都被复用，没有区段会被
 * 重建或重新排序两次；镜内是同一轴线上更窄的视锥，1 倍视锥总能包含它。投影取自 {@code render} HEAD 捕获的主投影，经
 * {@link ScopeRules#lensClipScale} 在裁剪空间中收窄（与全画面放大所见相同，包含视角摇晃与后坐力）。由于原版会在渲染中途重新
 * 绑定 {@code MinecraftClient#getFramebuffer()}，渲染期间把它指向 {@link #LENS_VIEW_TARGET}——边长为 {@link ScopeRules#lensViewSize}
 * （最多 512²）的方形目标。不画方块选框、云、天气、粒子与手；本次渲染的发光/本能描边像原版合成到主画面那样合成到镜内，随后
 * 在其上绘制墙内、水下与着火覆盖层，开镜玩家在方块内无法透视。
 * <p>
 * 渲染前后保存并恢复：帧缓冲字段、投影矩阵、模型视图栈、视口与绑定的帧缓冲；{@code WorldRenderer#render} 自身会重置雾
 * （{@code clearFog}）、混合与深度写入。随后 {@code scope_pip.json} 合成把镜内画面叠到主画面上，镜片效果与全画面放大相同
 * （畸变、边缘变暗、色调、污渍、反光条纹、镜内阴影），外加一圈短的暗色镜筒边。
 * <p>
 * 生命周期：首次使用时创建，尺寸变化时重建，离开画中画 2 秒后以及断线或资源重载时释放。任何失败都会关闭它并进入退避
 * （{@link ScopeRules#retryDelaySeconds}），期间 {@link #ready()} 为 false，开镜按全画面放大渲染。「极佳」画质与 Iris 光影包
 * 永远不会进入本类（{@link ScopeRules#effectiveMode}）。仅渲染线程；仅用于展示。
 */
public final class ScopePictureInPicture {
    public static final Identifier EFFECT = SparkWitch.id("shaders/post/scope_pip.json");
    /** The composite program; its pass receives the per-frame uniforms. / 合成程序；其 pass 接收逐帧 uniform。 */
    public static final String LENS_PROGRAM = "sparkwitch_scope_pip";
    /** The square lens render target, resized after every {@code setupDimensions}. / 方形镜内渲染目标。 */
    public static final String LENS_VIEW_TARGET = "scope_lens_view";
    /** Vanilla's near plane and first-person hand FOV. / 原版近平面与第一人称手部视场角。 */
    private static final float NEAR_PLANE = 0.05F;
    private static final float OVERLAY_FOV_DEGREES = 70.0F;
    private static final Identifier RELOAD_LISTENER_ID = SparkWitch.id("scope_picture_in_picture");
    private static final Matrix4f MAIN_PROJECTION = new Matrix4f();
    private static final Matrix4f LENS_PROJECTION = new Matrix4f();
    private static boolean registered;
    private static boolean mainProjectionCaptured;
    private static boolean worldRenderedThisFrame;
    private static boolean renderingLens;
    @Nullable
    private static PostEffectProcessor processor;
    @Nullable
    private static Framebuffer processorTarget;
    @Nullable
    private static Framebuffer lensView;
    @Nullable
    private static PostEffectPass lensPass;
    private static int processorWidth = -1;
    private static int processorHeight = -1;
    private static double equivalentFovDegrees = 70.0;
    private static boolean failed;
    private static int failures;
    private static long retryAtNanos;

    private ScopePictureInPicture() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // Reload listeners apply on the render thread, where GL objects may be released.
        // 重载监听器在渲染线程执行，可在此安全释放 GL 对象。
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return RELOAD_LISTENER_ID;
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        reset();
                    }
                });
    }

    /**
     * True only inside the lens pass's {@code WorldRenderer#render}. Public for consumers whose world-render hooks
     * must not run twice per frame or must not draw into the lens.
     * 仅在镜内渲染的 {@code WorldRenderer#render} 期间为 true。公开给世界渲染钩子不能每帧运行两次、或不应画进镜内的使用方。
     */
    public static boolean isRenderingLens() {
        return renderingLens;
    }

    /**
     * False while the renderer backs off after a failure (the scope then renders as ZOOM_BLUR), or while PiP is not
     * available at all.
     * 渲染器在失败后退避期间（此时开镜按全画面放大渲染）或画中画完全不可用时为 false。
     */
    public static boolean ready() {
        return ScopeRules.PICTURE_IN_PICTURE_AVAILABLE && !(failed && Util.getMeasuringTimeNano() - retryAtNanos < 0L);
    }

    /**
     * The FOV ZOOM_BLUR would project with to show the lens at its on-screen scale this frame (for the reticle).
     * 本帧全画面放大要以同样屏幕比例显示镜内画面时的投影视场角（供分划使用）。
     */
    public static double equivalentFovDegrees() {
        return equivalentFovDegrees;
    }

    /**
     * {@code WorldRenderer#render} HEAD: remember the main pass's projection, and that the world was rendered this
     * frame.
     * {@code WorldRenderer#render} HEAD：记录主渲染的投影，以及本帧已渲染世界。
     */
    public static void captureMainProjection(Matrix4f projection) {
        if (!renderingLens && projection != null) {
            MAIN_PROJECTION.set(projection);
            mainProjectionCaptured = true;
            worldRenderedThisFrame = true;
        }
    }

    /**
     * Whether the main world pass ran since the previous call (once per frame, from {@code ScopeRuntime.beforeGui}).
     * {@code GameRenderer#render} reaches its {@code beginWrite} on every frame, also those that render no world; the
     * lens is then left alone.
     * 自上次调用以来主世界渲染是否执行过（每帧一次，由 {@code ScopeRuntime.beforeGui} 调用）。{@code GameRenderer#render} 每帧都会
     * 执行到其 {@code beginWrite}，包括不渲染世界的帧；这些帧不处理镜片。
     */
    static boolean consumeWorldRendered() {
        boolean rendered = worldRenderedThisFrame;
        worldRenderedThisFrame = false;
        return rendered;
    }

    /**
     * Right before {@code setupTerrain} in {@code WorldRenderer#render}: during the lens pass, {@code RenderSystem}
     * shows the main projection, so Sodium sees an unchanged camera and reuses its render lists instead of rebuilding
     * its visibility graph for the lens (and again for the next main pass).
     * {@code WorldRenderer#render} 中紧接在 {@code setupTerrain} 之前：镜内渲染期间让 {@code RenderSystem} 显示主投影，Sodium 便
     * 视相机为未变化并复用其渲染列表，而不会为镜内（以及下一次主渲染）重建可见性图。
     */
    public static void beforeTerrainSetup() {
        if (renderingLens) {
            RenderSystem.setProjectionMatrix(new Matrix4f(MAIN_PROJECTION), VertexSorter.BY_DISTANCE);
        }
    }

    /** Right after {@code setupTerrain}: back to the lens projection. / 紧接在 {@code setupTerrain} 之后：恢复镜内投影。 */
    public static void afterTerrainSetup() {
        if (renderingLens) {
            RenderSystem.setProjectionMatrix(new Matrix4f(LENS_PROJECTION), VertexSorter.BY_DISTANCE);
        }
    }

    /**
     * After each {@code beginWrite(Z)} inside {@code WorldRenderer#render}: during the lens pass, the viewport goes back
     * to the lens target (vanilla left it window-sized after an outline clear or post pass).
     * {@code WorldRenderer#render} 内每次 {@code beginWrite(Z)} 之后：镜内渲染期间把视口恢复到镜内目标（原版在描边清除或后处理
     * 之后会留下窗口尺寸的视口）。
     */
    public static void restoreLensViewport() {
        Framebuffer lens = lensView;
        if (renderingLens && lens != null) {
            RenderSystem.viewport(0, 0, lens.textureWidth, lens.textureHeight);
        }
    }

    /**
     * Releases the processor and clears the failure backoff; deferred to the render thread when called elsewhere.
     * 释放处理器并清除失败退避；在其他线程调用时推迟到渲染线程。
     */
    public static void reset() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(ScopePictureInPicture::reset);
            return;
        }
        release();
        failed = false;
        failures = 0;
        retryAtNanos = 0L;
        mainProjectionCaptured = false;
        worldRenderedThisFrame = false;
    }

    /** Closes the processor (no-op when none is open). / 关闭处理器（未打开时无操作）。 */
    public static void release() {
        if (processor != null) {
            processor.close();
            processor = null;
        }
        processorTarget = null;
        lensView = null;
        lensPass = null;
        processorWidth = -1;
        processorHeight = -1;
    }

    /** A frame without PiP: close the processor once it has idled long enough. / 未使用画中画的帧：空闲足够久后关闭。 */
    static void idle(long idleNanos) {
        if (processor != null && ScopeRules.releasesProcessor(idleNanos)) {
            release();
        }
    }

    /**
     * Renders the lens and composites it over the main framebuffer; render thread only, right before the GUI pass.
     * Returns false when nothing was drawn (the caller then draws ZOOM_BLUR's lens for this frame).
     * 渲染镜内画面并合成到主帧缓冲上；仅渲染线程，紧接在 GUI 之前。未绘制任何内容时返回 false（调用方本帧改画全画面放大的镜片）。
     *
     * @param zoom    eased lens FOV multiplier / 缓动后的镜内视场倍率
     * @param fade    lens fade-in, 0..1 / 镜片淡入，0..1
     * @param shadowX scope-shadow offset, lens radii, GUI +x right / 镜内阴影偏移（镜片半径，GUI +x 向右）
     * @param shadowY scope-shadow offset, lens radii, GUI +y down / 镜内阴影偏移（镜片半径，GUI +y 向下）
     */
    static boolean render(MinecraftClient client, RenderTickCounter tickCounter, float zoom, float fade, float shadowX,
                          float shadowY) {
        if (!mainProjectionCaptured || client.world == null) {
            return false;
        }
        PostEffectProcessor active = ensureProcessor(client);
        Framebuffer lens = lensView;
        PostEffectPass composite = lensPass;
        if (active == null || lens == null || composite == null) {
            return false;
        }
        Framebuffer main = client.getFramebuffer();
        GameRenderer gameRenderer = client.gameRenderer;
        Camera camera = gameRenderer.getCamera();
        double radius = ScopeLensGeometry.lensRadius(main.textureWidth, main.textureHeight);
        double mainFov = ((ScopeGameRendererInvoker) gameRenderer)
                .sparkwitch$scopeFov(camera, tickCounter.getTickDelta(true), true);
        Matrix4f lensProjection = new Matrix4f()
                .scaling(ScopeRules.lensClipScale(mainFov, zoom, main.textureWidth, radius),
                        ScopeRules.lensClipScale(mainFov, zoom, main.textureHeight, radius), 1.0F)
                .mul(MAIN_PROJECTION);
        Matrix4f viewRotation = new Matrix4f().rotation(camera.getRotation().conjugate(new Quaternionf()));
        try {
            renderLensWorld(client, tickCounter, camera, main, lens, viewRotation, lensProjection);
        } catch (RuntimeException exception) {
            fail(exception);
            return false;
        }
        equivalentFovDegrees = ScopeRules.equivalentFovDegrees(mainFov, zoom);

        JsonEffectShaderProgram program = composite.getProgram();
        program.getUniformByNameOrDummy("LensCenter").set(0.5F, 0.5F);
        program.getUniformByNameOrDummy("LensRadius").set(ScopeLensGeometry.LENS_RADIUS_SHARE);
        program.getUniformByNameOrDummy("LensMargin").set(ScopeRules.LENS_VIEW_MARGIN);
        // GL texture space has +y up; the GUI-axis shadow has +y down. / GL 纹理空间 +y 向上，GUI 阴影 +y 向下。
        program.getUniformByNameOrDummy("ShadowOffset").set(shadowX, -shadowY);
        program.getUniformByNameOrDummy("Fade").set(fade);
        // Same state vanilla sets before its own post pass. / 与原版后处理前设置的状态一致。
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        try {
            active.render(tickCounter.getLastFrameDuration());
            return true;
        } catch (RuntimeException exception) {
            fail(exception);
            return false;
        } finally {
            main.beginWrite(false);
        }
    }

    /**
     * The second world pass into the lens target; every piece of state it changes is restored in {@code finally}.
     * 渲染到镜内目标的第二次世界渲染；它改动的所有状态都在 {@code finally} 中恢复。
     */
    private static void renderLensWorld(MinecraftClient client, RenderTickCounter tickCounter, Camera camera,
                                        Framebuffer main, Framebuffer lens, Matrix4f viewRotation,
                                        Matrix4f lensProjection) {
        ScopeMinecraftClientAccessor framebufferField = (ScopeMinecraftClientAccessor) client;
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        RenderSystem.backupProjectionMatrix();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        framebufferField.sparkwitch$setScopeFramebuffer(lens);
        LENS_PROJECTION.set(lensProjection);
        renderingLens = true;
        try {
            lens.beginWrite(true);
            RenderSystem.setProjectionMatrix(lensProjection, VertexSorter.BY_DISTANCE);
            client.worldRenderer.render(tickCounter, false, camera, client.gameRenderer,
                    client.gameRenderer.getLightmapTextureManager(), viewRotation, lensProjection);
            // The pass's glowing/instinct outlines sit 1:1 in the corner of the window-sized outline target, so the
            // window-sized composite vanilla uses for the main view lands exactly on the lens target.
            // 本次渲染的发光/本能描边 1:1 位于窗口尺寸描边目标的一角，因此原版用于主画面的窗口尺寸合成恰好落在镜内目标上。
            lens.beginWrite(false);
            client.worldRenderer.drawEntityOutlinesFramebuffer();
            // In-wall, underwater and fire overlays at the unzoomed hand FOV, like vanilla's hand pass.
            // 以未放大的手部视场角绘制墙内、水下与着火覆盖层，与原版手部渲染相同。
            lens.beginWrite(true);
            RenderSystem.clear(256, MinecraftClient.IS_SYSTEM_MAC);
            RenderSystem.setProjectionMatrix(new Matrix4f().setPerspective(
                    (float) Math.toRadians(OVERLAY_FOV_DEGREES), 1.0F, NEAR_PLANE,
                    client.gameRenderer.getFarPlaneDistance()), VertexSorter.BY_DISTANCE);
            InGameOverlayRenderer.renderOverlays(client, new MatrixStack());
        } finally {
            renderingLens = false;
            framebufferField.sparkwitch$setScopeFramebuffer(main);
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            main.beginWrite(true);
        }
    }

    @Nullable
    private static PostEffectProcessor ensureProcessor(MinecraftClient client) {
        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer.textureWidth <= 0 || framebuffer.textureHeight <= 0) {
            return null;
        }
        if (failed) {
            if (Util.getMeasuringTimeNano() - retryAtNanos < 0L) {
                return null;
            }
            // Backoff elapsed: rebuild from scratch. / 退避结束：从头重建。
            failed = false;
        }
        if (processor != null && processorTarget != framebuffer) {
            release();
        }
        try {
            if (processor == null) {
                processor = new PostEffectProcessor(client.getTextureManager(), client.getResourceManager(),
                        framebuffer, EFFECT);
                processorTarget = framebuffer;
                processorWidth = -1;
                processorHeight = -1;
                lensPass = findLensPass(processor);
                lensView = processor.getSecondaryTarget(LENS_VIEW_TARGET);
                if (lensView == null || !lensView.useDepthAttachment) {
                    throw new IllegalStateException("scope_pip.json lacks a depth-backed " + LENS_VIEW_TARGET);
                }
            }
            if (processorWidth != framebuffer.textureWidth || processorHeight != framebuffer.textureHeight) {
                // First use or window resize: every target follows the framebuffer, then the lens view is made square
                // again (the scope vertex shader maps each pass to its own output, whatever its size).
                // 首次使用或窗口尺寸变化：所有目标跟随帧缓冲，随后再把镜内目标改回方形（开镜顶点着色器按各 pass 自己的输出
                // 尺寸映射，与尺寸无关）。
                processor.setupDimensions(framebuffer.textureWidth, framebuffer.textureHeight);
                int side = ScopeRules.lensViewSize(framebuffer.textureWidth, framebuffer.textureHeight);
                lensView.resize(side, side, MinecraftClient.IS_SYSTEM_MAC);
                processorWidth = framebuffer.textureWidth;
                processorHeight = framebuffer.textureHeight;
            }
            return processor;
        } catch (IOException | RuntimeException exception) {
            // JsonSyntaxException, GL compile errors and resize failures: ZOOM_BLUR until the retry.
            // JsonSyntaxException、GL 编译错误与尺寸重建失败：重试前按全画面放大渲染。
            fail(exception);
            return null;
        }
    }

    private static PostEffectPass findLensPass(PostEffectProcessor processor) {
        for (PostEffectPass pass : ((PostEffectProcessorAccessor) processor).sparkwitch$getPasses()) {
            if (LENS_PROGRAM.equals(pass.getName())) {
                return pass;
            }
        }
        throw new IllegalStateException("scope_pip.json lacks the " + LENS_PROGRAM + " pass");
    }

    private static void fail(Exception exception) {
        failures++;
        int retrySeconds = ScopeRules.retryDelaySeconds(failures);
        if (failures == 1) {
            SparkWitch.LOGGER.warn("The picture-in-picture scope failed on {} / {} / OpenGL {}; using Full-Screen "
                            + "Zoom, retrying in {} s", GlDebugInfo.getRenderer(), GlDebugInfo.getVendor(),
                    GlDebugInfo.getVersion(), retrySeconds, exception);
        } else {
            SparkWitch.LOGGER.warn("The picture-in-picture scope failed again (#{}): {}; retrying in {} s", failures,
                    exception.toString(), retrySeconds);
        }
        release();
        failed = true;
        retryAtNanos = Util.getMeasuringTimeNano() + retrySeconds * 1_000_000_000L;
    }
}
