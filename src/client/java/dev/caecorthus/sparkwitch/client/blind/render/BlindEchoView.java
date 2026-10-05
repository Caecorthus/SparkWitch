package dev.caecorthus.sparkwitch.client.blind.render;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlDebugInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.blind.BlindPerceptionClientState;
import dev.caecorthus.sparkwitch.client.blind.BlindView;
import dev.caecorthus.sparkwitch.client.mixin.PostEffectProcessorAccessor;
import dev.caecorthus.sparkwitch.client.render.WraithClientState;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.JsonEffectShaderProgram;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The Blind's screen (C4, C13, D1): a PRIVATE post processor (Seeker pattern, never
 * {@code GameRenderer.postProcessor}).
 * <ol>
 *   <li>{@link #captureWorld} at {@code WorldRenderEvents.BEFORE_DEBUG_RENDER}: flushes the pending block-entity layer
 *   (Wathe doors) and the Fast/Fancy dropped-item layer, copies the main depth into {@code blind_depth}, re-binds main,
 *   records this frame's projection, view rotation and camera, and re-renders perceived bodies into {@code blind_sil}
 *   ({@link BlindSilhouettePass}).</li>
 *   <li>{@link #renderFrame} right after {@code GameRenderer#render}'s {@code Framebuffer#beginWrite(Z)} (after every
 *   other SparkWitch filter, before the GUI): writes black plus white line art over the whole main framebuffer, so
 *   every earlier image (sky, particles, outlines, other filters) is consumed; the entity-outline framebuffer is never
 *   composited again.</li>
 * </ol>
 * Fails closed: with an Iris shader pack, a failed load, a missing capture or the camera off the Blind, the world is
 * painted black, never shown unfiltered; the HUD hint names the shader pack only when Iris reports one, and a failed
 * pipeline is rebuilt after a growing backoff ({@link BlindEchoMode#retryDelaySeconds}). Released on every inactive
 * frame; {@link #reset()} on disconnect and resource reload.
 * 盲人的画面（C4、C13、D1）：私有后处理器（搜寻者模式，从不使用 {@code GameRenderer.postProcessor}）。
 * 1）{@link #captureWorld} 于 {@code WorldRenderEvents.BEFORE_DEBUG_RENDER}：先刷新待绘制的方块实体层（Wathe 车门）
 * 与 Fast/Fancy 下的掉落物层，把主深度拷贝到 {@code blind_depth}，重新绑定主帧缓冲，记录本帧投影、视角旋转与
 * 相机位置，并把被感知身体重新渲染到 {@code blind_sil}（{@link BlindSilhouettePass}）。2）{@link #renderFrame} 紧接在 {@code GameRenderer#render} 的
 * {@code Framebuffer#beginWrite(Z)} 之后（其他所有 SparkWitch 滤镜之后、GUI 之前）：把整个主帧缓冲写成黑底白线，
 * 吞掉之前的所有画面（天空、粒子、描边、其他滤镜），且不再合成实体描边帧缓冲。
 * 失败即全黑：开着 Iris 光影包、加载失败、缺少捕获或镜头不在盲人身上时，世界画成全黑，绝不显示未过滤画面；
 * 只有 Iris 报告光影包时 HUD 提示才提到光影包，失败的管线会在逐渐变长的退避后重建
 * （{@link BlindEchoMode#retryDelaySeconds}）。非激活帧都会释放；断线与资源重载时调用 {@link #reset()}。
 */
public final class BlindEchoView {
    public static final Identifier EFFECT = SparkWitch.id("shaders/post/blind_echo.json");
    static final String DEPTH_TARGET = "blind_depth";
    static final String SILHOUETTE_TARGET = "blind_sil";
    static final String ECHO_PROGRAM = "sparkwitch_blind_echo";
    static final String BLUR_PROGRAM = "sparkwitch_blind_blur";
    /** Feet and head sample heights (fractions of the body height) for "partly hidden". / “部分被遮挡”的脚与头采样高度。 */
    private static final double FEET = 0.1;
    private static final double HEAD = 0.9;
    @Nullable
    private static PostEffectProcessor processor;
    @Nullable
    private static Framebuffer processorTarget;
    private static int processorWidth = -1;
    private static int processorHeight = -1;
    private static boolean failed;
    /** Failures since the last reset; drives the rebuild backoff. / 自上次重置以来的失败次数，决定重建退避。 */
    private static int failures;
    private static long retryAtNanos;
    private static boolean captured;
    private static BlindEchoMode.Hint hint = BlindEchoMode.Hint.NONE;
    private static int missedCaptures;
    private static double cameraX;
    private static double cameraY;
    private static double cameraZ;
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW_ROTATION = new Matrix4f();
    private static final BlindEchoUniforms UNIFORMS = new BlindEchoUniforms();
    private static final List<AbstractClientPlayerEntity> BODIES = new ArrayList<>();
    private static final List<BlindEchoUniforms.Body> BODY_CENTRES = new ArrayList<>();

    private BlindEchoView() {
    }

    /** {@code WorldRenderEvents.BEFORE_DEBUG_RENDER}; render thread. / 渲染线程。 */
    public static void captureWorld(WorldRenderContext context) {
        captured = false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!BlindView.isActive(client) || !seesOwnView(client) || BlindShaderPackCheck.isShaderPackInUse()) {
            return;
        }
        PostEffectProcessor active = ensureProcessor(client);
        if (active == null) {
            return;
        }
        Framebuffer main = client.getFramebuffer();
        Framebuffer depth = active.getSecondaryTarget(DEPTH_TARGET);
        Framebuffer silhouette = active.getSecondaryTarget(SILHOUETTE_TARGET);
        if (depth == null || silhouette == null) {
            fail(new IllegalStateException("blind_echo.json lacks its capture targets"));
            return;
        }
        try {
            // Block entities with custom layers (Wathe doors) and, in Fast/Fancy, dropped items (C6: the fixed
            // entity-translucent-cull layer) are still pending here; vanilla flushes both right after the debug render.
            // 使用自定义层的方块实体（Wathe 车门）以及 Fast/Fancy 下的掉落物（C6：固定的实体半透明剔除层）此时仍待绘制；
            // 原版在调试渲染之后紧接着刷新这两者。
            if (context.consumers() instanceof VertexConsumerProvider.Immediate immediate) {
                immediate.drawCurrentLayer();
                immediate.draw(TexturedRenderLayers.getEntityTranslucentCull());
            }
            depth.copyDepthFrom(main);
            // copyDepthFrom leaves FBO 0 bound. / copyDepthFrom 结束时绑定的是 FBO 0。
            main.beginWrite(false);
            PROJECTION.set(context.projectionMatrix());
            VIEW_ROTATION.set(context.positionMatrix());
            Vec3d camera = context.camera().getPos();
            cameraX = camera.x;
            cameraY = camera.y;
            cameraZ = camera.z;
            float tickDelta = context.tickCounter().getTickDelta(true);
            collectBodies(client, tickDelta, Util.getMeasuringTimeNano());
            BlindSilhouettePass.render(client, silhouette, main, BODIES, PROJECTION, VIEW_ROTATION, camera, tickDelta);
            captured = true;
        } catch (RuntimeException exception) {
            main.beginWrite(false);
            fail(exception);
        }
    }

    /**
     * Called right after {@code GameRenderer#render} binds the main framebuffer for the GUI; render thread.
     * 在 {@code GameRenderer#render} 为 GUI 绑定主帧缓冲之后立即调用；渲染线程。
     */
    public static void renderFrame(MinecraftClient client) {
        boolean wasCaptured = captured;
        captured = false;
        boolean active = BlindView.isActive(client);
        boolean ownView = seesOwnView(client);
        boolean shaderPack = active && BlindShaderPackCheck.isShaderPackInUse();
        missedCaptures = active && ownView && !shaderPack && !failed && !wasCaptured
                ? Math.min(missedCaptures + 1, BlindEchoMode.HINT_AFTER_MISSED_CAPTURES)
                : 0;
        hint = BlindEchoMode.hint(active, shaderPack, failed, missedCaptures);
        switch (BlindEchoMode.resolve(active, ownView, shaderPack, failed, wasCaptured)) {
            case OFF -> release();
            case BLACK -> {
                if (shaderPack) {
                    // Iris owns the frame; drop our GL targets until the pack is turned off.
                    // Iris 接管了画面；在关闭光影包之前释放我们的 GL 目标。
                    release();
                }
                fillBlack(client);
            }
            case ECHO -> {
                if (!runPasses(client)) {
                    fillBlack(client);
                }
            }
        }
    }

    /** The HUD hint for this frame's black view. / 本帧黑屏对应的 HUD 提示。 */
    public static BlindEchoMode.Hint currentHint() {
        return hint;
    }

    /**
     * Releases GL objects on disconnect or resource reload and allows a fresh load; deferred to the render thread when
     * called elsewhere (connection events can fire on the network thread).
     * 断线或资源重载时释放 GL 对象并允许重新加载；在其他线程调用时推迟到渲染线程（连接事件可能在网络线程触发）。
     */
    public static void reset() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(BlindEchoView::reset);
            return;
        }
        release();
        BlindSilhouettePass.close();
        failed = false;
        failures = 0;
        retryAtNanos = 0L;
        captured = false;
        hint = BlindEchoMode.Hint.NONE;
        missedCaptures = 0;
    }

    /** Closes the processor (no-op when none is open). / 关闭处理器（未打开时无操作）。 */
    public static void release() {
        if (processor != null) {
            processor.close();
            processor = null;
        }
        processorTarget = null;
        processorWidth = -1;
        processorHeight = -1;
        BODIES.clear();
        BODY_CENTRES.clear();
    }

    private static boolean runPasses(MinecraftClient client) {
        PostEffectProcessor active = processor;
        if (active == null) {
            return false;
        }
        long now = Util.getMeasuringTimeNano();
        UNIFORMS.packPulses(BlindPerceptionClientState.get().activePulses(now), now, cameraX, cameraY, cameraZ);
        UNIFORMS.packPlayers(BODY_CENTRES, cameraX, cameraY, cameraZ);
        UNIFORMS.setView(PROJECTION, VIEW_ROTATION);
        float echoTime = BlindEchoUniforms.echoTime(now);
        boolean bodies = UNIFORMS.playerCount() > 0;
        boolean blur = UNIFORMS.blurRange() > 0.0f;
        // Same state vanilla sets before its own post pass. / 与原版后处理前设置的状态一致。
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        try {
            for (PostEffectPass pass : ((PostEffectProcessorAccessor) active).sparkwitch$getPasses()) {
                String name = pass.getName();
                if (!bodies && !ECHO_PROGRAM.equals(name)) {
                    // No perceived body: the silhouette mask and blur passes have nothing to do.
                    // 没有被感知身体：轮廓遮罩与模糊 pass 无事可做。
                    continue;
                }
                if (!blur && BLUR_PROGRAM.equals(name)) {
                    // Every hidden body is within ~3 blocks: the echo pass reads no halo (BlurRange 0).
                    // 所有被遮挡身体都在约 3 格内：回声 pass 不读取光晕（BlurRange 为 0）。
                    continue;
                }
                JsonEffectShaderProgram program = pass.getProgram();
                program.getUniformByNameOrDummy("InvViewProj").set(UNIFORMS.inverseViewProjection());
                program.getUniformByNameOrDummy("PulseCount").set((float) UNIFORMS.pulseCount());
                program.getUniformByNameOrDummy("PulseData").set(UNIFORMS.pulseData());
                program.getUniformByNameOrDummy("PlayerCount").set((float) UNIFORMS.playerCount());
                program.getUniformByNameOrDummy("PlayerData").set(UNIFORMS.playerData());
                program.getUniformByNameOrDummy("PlayerHidden").set(UNIFORMS.playerHidden());
                program.getUniformByNameOrDummy("BlurRange").set(UNIFORMS.blurRange());
                program.getUniformByNameOrDummy("EchoTime").set(echoTime);
                pass.render(0.0F);
            }
        } catch (RuntimeException exception) {
            fail(exception);
            return false;
        } finally {
            client.getFramebuffer().beginWrite(true);
        }
        return true;
    }

    /**
     * The Blind looks through its own living eyes ({@link BlindEchoMode#seesOwnView}); otherwise the active view is
     * black. 盲人通过自己存活的视角观看（{@link BlindEchoMode#seesOwnView}）；否则激活的视图为全黑。
     */
    private static boolean seesOwnView(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        return player != null && BlindEchoMode.seesOwnView(client.getCameraEntity() == player, player.isSpectator(),
                NoellesTaotieSeekerBridge.isSwallowed(player));
    }

    private static void fillBlack(MinecraftClient client) {
        client.getFramebuffer().beginWrite(true);
        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
        RenderSystem.clear(GlConst.GL_COLOR_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
    }

    private static void collectBodies(MinecraftClient client, float tickDelta, long now) {
        BODIES.clear();
        BODY_CENTRES.clear();
        ClientWorld world = client.world;
        if (world == null) {
            return;
        }
        BlindPerceptionClientState state = BlindPerceptionClientState.get();
        for (AbstractClientPlayerEntity other : world.getPlayers()) {
            if (drawsBody(other != client.player, other.isSpectator(), other.isRemoved(),
                    WraithClientState.isActive(other), state.isPerceived(other.getId(), now))) {
                BODIES.add(other);
            }
        }
        BlindEchoUniforms.keepNearest(BODIES, body -> body.squaredDistanceTo(cameraX, cameraY, cameraZ),
                BlindEchoUniforms.MAX_PLAYERS);
        Vec3d camera = new Vec3d(cameraX, cameraY, cameraZ);
        for (AbstractClientPlayerEntity body : BODIES) {
            double x = MathHelper.lerp(tickDelta, body.lastRenderX, body.getX());
            double y = MathHelper.lerp(tickDelta, body.lastRenderY, body.getY());
            double z = MathHelper.lerp(tickDelta, body.lastRenderZ, body.getZ());
            double height = body.getHeight();
            // Lines of sight once per body, not per pixel: the centre drives the ripple; a blocked centre, feet or
            // head makes the body count for the blur reach.
            // 每个身体只算一次视线（而非逐像素）：中心决定涟漪；中心、脚或头任一被挡住即计入模糊覆盖距离。
            Vec3d centre = new Vec3d(x, y + height * 0.5, z);
            double centreDistance = camera.distanceTo(centre);
            double centreHit = blockHitDistance(world, camera, centre);
            boolean partlyHidden = BlindEchoUniforms.occludes(centreDistance, centreHit)
                    || occluded(world, camera, new Vec3d(x, y + height * FEET, z))
                    || occluded(world, camera, new Vec3d(x, y + height * HEAD, z));
            BODY_CENTRES.add(new BlindEchoUniforms.Body(centre.x, centre.y, centre.z, 1.0f,
                    BlindEchoUniforms.hiddenWeight(centreDistance, centreHit), partlyHidden));
        }
    }

    /**
     * Pure rule: a perceived, present other player that is not a spectator (dead, swallowed, Last Stand pending) and
     * not an active Wraith (never perceivable, D4) gets a silhouette and a ripple.
     * 纯规则：被感知、仍存在、非旁观者（死亡、被吞、最后一搏待定）且非活跃冤魂（永远不可被感知，D4）的其他玩家才有轮廓与涟漪。
     */
    static boolean drawsBody(boolean otherPlayer, boolean spectator, boolean removed, boolean activeWraith,
                             boolean perceived) {
        return otherPlayer && !spectator && !removed && !activeWraith && perceived;
    }

    private static boolean occluded(ClientWorld world, Vec3d camera, Vec3d point) {
        return BlindEchoUniforms.occludes(camera.distanceTo(point), blockHitDistance(world, camera, point));
    }

    /** Distance to the first block on the line of sight, or infinity. / 视线上第一个方块的距离，无则为无穷大。 */
    private static double blockHitDistance(ClientWorld world, Vec3d from, Vec3d to) {
        BlockHitResult hit = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.VISUAL,
                RaycastContext.FluidHandling.NONE, ShapeContext.absent()));
        return hit.getType() == HitResult.Type.MISS ? Double.POSITIVE_INFINITY : from.distanceTo(hit.getPos());
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
            // Backoff elapsed: rebuild from scratch; failing again stays black and waits longer (C13).
            // 退避结束：从头重建；再次失败仍保持全黑并等待更久（C13）。
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
            }
            if (processorWidth != framebuffer.textureWidth || processorHeight != framebuffer.textureHeight) {
                // First use or window resize: (re)build the private targets in place; a failure here stays black too.
                // 首次使用或窗口尺寸变化：原地（重新）构建私有目标；此处失败同样保持全黑。
                processor.setupDimensions(framebuffer.textureWidth, framebuffer.textureHeight);
                processorWidth = framebuffer.textureWidth;
                processorHeight = framebuffer.textureHeight;
            }
            return processor;
        } catch (IOException | RuntimeException exception) {
            // Includes JsonSyntaxException, GL compile errors and resize failures: stay black (C13) until the retry.
            // 包括 JsonSyntaxException、GL 编译错误与尺寸重建失败：重试前保持全黑（C13）。
            fail(exception);
            return null;
        }
    }

    private static void fail(Exception exception) {
        failures++;
        int retrySeconds = BlindEchoMode.retryDelaySeconds(failures);
        if (failures == 1) {
            // The stack and the driver are what a report of a black Blind view without shader packs needs.
            // 没有光影包却黑屏的问题报告需要堆栈与显卡驱动信息。
            SparkWitch.LOGGER.warn("The Blind's echo view failed on {} / {} / OpenGL {}; the screen stays black, "
                            + "retrying in {} s", GlDebugInfo.getRenderer(), GlDebugInfo.getVendor(),
                    GlDebugInfo.getVersion(), retrySeconds, exception);
        } else {
            SparkWitch.LOGGER.warn("The Blind's echo view failed again (#{}): {}; retrying in {} s", failures,
                    exception.toString(), retrySeconds);
        }
        release();
        failed = true;
        retryAtNanos = Util.getMeasuringTimeNano() + retrySeconds * 1_000_000_000L;
    }
}
