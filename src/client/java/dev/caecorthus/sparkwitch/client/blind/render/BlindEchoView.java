package dev.caecorthus.sparkwitch.client.blind.render;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.blind.BlindPerceptionClientState;
import dev.caecorthus.sparkwitch.client.blind.BlindView;
import dev.caecorthus.sparkwitch.client.mixin.PostEffectProcessorAccessor;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.JsonEffectShaderProgram;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
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
 *   (Wathe doors), copies the main depth into {@code blind_depth}, re-binds main, records this frame's projection, view
 *   rotation and camera, and re-renders perceived bodies into {@code blind_sil} ({@link BlindSilhouettePass}).</li>
 *   <li>{@link #renderFrame} right after {@code GameRenderer#render}'s {@code Framebuffer#beginWrite(Z)} (after every
 *   other SparkWitch filter, before the GUI): writes black plus white line art over the whole main framebuffer, so
 *   every earlier image (sky, particles, outlines, other filters) is consumed; the entity-outline framebuffer is never
 *   composited again.</li>
 * </ol>
 * Fails closed: with an Iris shader pack, a failed load, a missing capture or the camera off the Blind, the world is
 * painted black (with a HUD hint for fixable cases), never shown unfiltered. Released on every inactive frame;
 * {@link #reset()} on disconnect and resource reload.
 * 盲人的画面（C4、C13、D1）：私有后处理器（搜寻者模式，从不使用 {@code GameRenderer.postProcessor}）。
 * 1）{@link #captureWorld} 于 {@code WorldRenderEvents.BEFORE_DEBUG_RENDER}：先刷新待绘制的方块实体层（Wathe 车门），
 * 把主深度拷贝到 {@code blind_depth}，重新绑定主帧缓冲，记录本帧投影、视角旋转与相机位置，并把被感知身体重新渲染到
 * {@code blind_sil}（{@link BlindSilhouettePass}）。2）{@link #renderFrame} 紧接在 {@code GameRenderer#render} 的
 * {@code Framebuffer#beginWrite(Z)} 之后（其他所有 SparkWitch 滤镜之后、GUI 之前）：把整个主帧缓冲写成黑底白线，
 * 吞掉之前的所有画面（天空、粒子、描边、其他滤镜），且不再合成实体描边帧缓冲。
 * 失败即全黑：开着 Iris 光影包、加载失败、缺少捕获或镜头不在盲人身上时，世界画成全黑（可自行解决的情况附 HUD 提示），
 * 绝不显示未过滤画面。非激活帧都会释放；断线与资源重载时调用 {@link #reset()}。
 */
public final class BlindEchoView {
    public static final Identifier EFFECT = SparkWitch.id("shaders/post/blind_echo.json");
    static final String DEPTH_TARGET = "blind_depth";
    static final String SILHOUETTE_TARGET = "blind_sil";
    static final String ECHO_PROGRAM = "sparkwitch_blind_echo";
    @Nullable
    private static PostEffectProcessor processor;
    @Nullable
    private static Framebuffer processorTarget;
    private static int processorWidth = -1;
    private static int processorHeight = -1;
    private static boolean failed;
    private static boolean captured;
    private static boolean hintVisible;
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
            // Block entities with custom layers (Wathe doors) are still pending here; vanilla flushes them next anyway.
            // 使用自定义层的方块实体（Wathe 车门）此时仍待绘制；原版紧接着也会刷新它们。
            if (context.consumers() instanceof VertexConsumerProvider.Immediate immediate) {
                immediate.drawCurrentLayer();
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
        hintVisible = BlindEchoMode.showsHint(active, shaderPack, failed, missedCaptures);
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

    /** Whether the HUD should show the "turn shader packs off" hint. / HUD 是否应显示“请关闭光影包”提示。 */
    public static boolean isFallbackHintVisible() {
        return hintVisible;
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
        captured = false;
        hintVisible = false;
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
        // Same state vanilla sets before its own post pass. / 与原版后处理前设置的状态一致。
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        try {
            for (PostEffectPass pass : ((PostEffectProcessorAccessor) active).sparkwitch$getPasses()) {
                if (!bodies && !ECHO_PROGRAM.equals(pass.getName())) {
                    // No perceived body: the silhouette mask and blur passes have nothing to do.
                    // 没有被感知身体：轮廓遮罩与模糊 pass 无事可做。
                    continue;
                }
                JsonEffectShaderProgram program = pass.getProgram();
                program.getUniformByNameOrDummy("InvViewProj").set(UNIFORMS.inverseViewProjection());
                program.getUniformByNameOrDummy("ViewProj").set(UNIFORMS.viewProjection());
                program.getUniformByNameOrDummy("PulseCount").set((float) UNIFORMS.pulseCount());
                program.getUniformByNameOrDummy("PulseData").set(UNIFORMS.pulseData());
                program.getUniformByNameOrDummy("PlayerCount").set((float) UNIFORMS.playerCount());
                program.getUniformByNameOrDummy("PlayerData").set(UNIFORMS.playerData());
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
     * The camera is on the Blind and it is not swallowed (C7: a swallowed Blind stays fully black, no lines).
     * 镜头在盲人身上且未被吞下（C7：被吞下的盲人保持全黑，没有线稿）。
     */
    private static boolean seesOwnView(MinecraftClient client) {
        return client.player != null && client.getCameraEntity() == client.player
                && !NoellesTaotieSeekerBridge.isSwallowed(client.player);
    }

    private static void fillBlack(MinecraftClient client) {
        client.getFramebuffer().beginWrite(true);
        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
        RenderSystem.clear(GlConst.GL_COLOR_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
    }

    private static void collectBodies(MinecraftClient client, float tickDelta, long now) {
        BODIES.clear();
        BODY_CENTRES.clear();
        if (client.world == null) {
            return;
        }
        BlindPerceptionClientState state = BlindPerceptionClientState.get();
        for (AbstractClientPlayerEntity other : client.world.getPlayers()) {
            if (other != client.player && !other.isSpectator() && !other.isRemoved()
                    && state.isPerceived(other.getId(), now)) {
                BODIES.add(other);
            }
        }
        BlindEchoUniforms.keepNearest(BODIES, body -> body.squaredDistanceTo(cameraX, cameraY, cameraZ),
                BlindEchoUniforms.MAX_PLAYERS);
        for (AbstractClientPlayerEntity body : BODIES) {
            BODY_CENTRES.add(new BlindEchoUniforms.Body(
                    MathHelper.lerp(tickDelta, body.lastRenderX, body.getX()),
                    MathHelper.lerp(tickDelta, body.lastRenderY, body.getY()) + body.getHeight() * 0.5,
                    MathHelper.lerp(tickDelta, body.lastRenderZ, body.getZ()),
                    1.0f));
        }
    }

    @Nullable
    private static PostEffectProcessor ensureProcessor(MinecraftClient client) {
        Framebuffer framebuffer = client.getFramebuffer();
        if (failed || framebuffer.textureWidth <= 0 || framebuffer.textureHeight <= 0) {
            return null;
        }
        if (processor != null && processorTarget != framebuffer) {
            release();
        }
        if (processor != null) {
            if (processorWidth != framebuffer.textureWidth || processorHeight != framebuffer.textureHeight) {
                // Window resize: rebuild the private targets in place. / 窗口尺寸变化：原地重建私有目标。
                processor.setupDimensions(framebuffer.textureWidth, framebuffer.textureHeight);
                processorWidth = framebuffer.textureWidth;
                processorHeight = framebuffer.textureHeight;
            }
            return processor;
        }
        try {
            processor = new PostEffectProcessor(client.getTextureManager(), client.getResourceManager(), framebuffer,
                    EFFECT);
            processor.setupDimensions(framebuffer.textureWidth, framebuffer.textureHeight);
            processorTarget = framebuffer;
            processorWidth = framebuffer.textureWidth;
            processorHeight = framebuffer.textureHeight;
            return processor;
        } catch (IOException | RuntimeException exception) {
            // Includes JsonSyntaxException and GL compile errors: stay black (C13) until reset.
            // 包括 JsonSyntaxException 与 GL 编译错误：重置前保持全黑（C13）。
            fail(exception);
            return null;
        }
    }

    private static void fail(Exception exception) {
        SparkWitch.LOGGER.warn("The Blind's echo view failed; the screen stays black", exception);
        release();
        failed = true;
    }
}
