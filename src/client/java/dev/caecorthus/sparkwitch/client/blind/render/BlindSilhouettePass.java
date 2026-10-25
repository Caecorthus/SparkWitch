package dev.caecorthus.sparkwitch.client.blind.render;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.render.entity.EntityModelCapture;
import dev.caecorthus.sparkwitch.client.render.entity.EntityRedraw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Draws the perceived players' bodies, white and flat, into the private {@code blind_sil} target with its own depth
 * test, so the echo pass can show the parts a wall hides (D1). Entity render protocol (2026-10-25): a body the world
 * pass drew this frame reuses exactly that geometry ({@link EntityModelCapture}, opened by {@link #beginFrame} at
 * {@code WorldRenderEvents.START}), so its silhouette matches the line art even when Entity Model Features and an
 * animation pack pose it; re-rendering it made the two poses differ and the silhouette twitch and double up (playtest
 * 2026-10-25). Only a body the world pass did not draw (culled behind walls) is drawn again, through
 * {@link EntityRedraw}, with {@link #isRendering()} true so {@code BlindEchoSilhouetteMixin} drops feature renderers
 * (held items, armor, capes) and name labels and the shape never shows a knife or gun; the world pass already skips
 * them on other players for the Blind. Render thread only; {@link #render} runs at {@code BEFORE_DEBUG_RENDER}, before
 * the world depth copy.
 * 把被感知玩家的身体以纯白画到私有 {@code blind_sil} 目标（自带深度测试），让回声 pass 能显示被墙挡住的部分（D1）。
 * 实体渲染协议（2026-10-25）：世界 pass 本帧画过的身体直接复用那份几何（{@link EntityModelCapture}，由 {@link #beginFrame}
 * 在 {@code WorldRenderEvents.START} 打开），即使 Entity Model Features 与动画包为它摆姿势，轮廓也与线稿吻合；此前的重新渲染
 * 让两个姿势不一致，轮廓抽搐、重影（2026-10-25 试玩）。只有世界 pass 未画的身体（被墙后剔除）才经 {@link EntityRedraw} 重画，
 * 期间 {@link #isRendering()} 为真，{@code BlindEchoSilhouetteMixin} 会去掉特征渲染器（手持物、盔甲、披风）与名字标签，
 * 轮廓不会露出刀或枪；世界 pass 本就为盲人跳过其他玩家的这些内容。仅限渲染线程；{@link #render} 在
 * {@code BEFORE_DEBUG_RENDER} 执行，早于世界深度拷贝。
 */
public final class BlindSilhouettePass {
    private static final int INITIAL_BUFFER_BYTES = 64 * 1024;
    private static boolean rendering;
    private static boolean warned;
    @Nullable
    private static BufferAllocator allocator;
    @Nullable
    private static Frame frame;

    private BlindSilhouettePass() {
    }

    /** True only while a culled body is being drawn again. / 仅在重画被剔除的身体期间为真。 */
    public static boolean isRendering() {
        return rendering;
    }

    /**
     * {@code WorldRenderEvents.START}: starts this frame's white buffer and records the world pass's own draw of
     * {@code bodies}. Discards an unfinished earlier frame.
     * {@code WorldRenderEvents.START}：开始本帧的白色缓冲，并录制世界 pass 自己对 {@code bodies} 的绘制。丢弃未完成的上一帧。
     */
    static void beginFrame(List<AbstractClientPlayerEntity> bodies) {
        discardFrame();
        if (bodies.isEmpty()) {
            return;
        }
        Frame next = new Frame(bodies);
        frame = next;
        EntityModelCapture.open(next);
    }

    /** Ends the recording and drops this frame's vertices. / 结束录制并丢弃本帧顶点。 */
    static void discardFrame() {
        Frame open = takeFrame();
        if (open != null) {
            open.discard();
        }
    }

    /**
     * Clears {@code target} and draws {@code bodies} into it, then re-binds {@code main}. Bodies recorded since
     * {@link #beginFrame} keep the world pass's geometry; every other body is drawn again.
     * 清空 {@code target} 并把 {@code bodies} 画进去，然后重新绑定 {@code main}。自 {@link #beginFrame} 以来已录制的身体保留世界
     * pass 的几何；其他身体重新绘制。
     */
    static void render(MinecraftClient client, Framebuffer target, Framebuffer main,
                       List<AbstractClientPlayerEntity> bodies, Matrix4f projection, Matrix4f viewRotation,
                       Vec3d camera, float tickDelta) {
        Frame recorded = takeFrame();
        target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        target.clear(MinecraftClient.IS_SYSTEM_MAC);
        if (bodies.isEmpty()) {
            if (recorded != null) {
                recorded.discard();
            }
            main.beginWrite(false);
            return;
        }
        // No START this frame: nothing was recorded, so every body is drawn again.
        // 本帧没有 START：没有录制任何内容，所有身体都重新绘制。
        Frame current = recorded != null ? recorded : new Frame(bodies);
        VertexConsumerProvider provider = layer -> current.white;
        BuiltBuffer built;
        rendering = true;
        try {
            for (AbstractClientPlayerEntity body : bodies) {
                if (!current.recorded.contains(body)) {
                    redrawBody(client, body, camera, tickDelta, provider);
                }
            }
        } finally {
            rendering = false;
            // Ended even when a redraw escapes with an Error, so a kept consumer or provider is dead from here on.
            // 即使重画以 Error 逃逸也会结束，被保留的消费者或提供器从此失效。
            built = current.end();
        }
        if (built == null) {
            main.beginWrite(false);
            return;
        }
        target.beginWrite(false);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(viewRotation);
        RenderSystem.applyModelViewMatrix();
        // Own copy instead of the shared backup slot, which callers up the stack may be using.
        // 自行保存副本，而不是使用调用栈上层可能正在用的共享备份槽。
        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorter savedSorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(projection, VertexSorter.BY_DISTANCE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GlConst.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        try {
            BufferRenderer.drawWithGlobalProgram(built);
        } finally {
            RenderSystem.enableCull();
            RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            main.beginWrite(false);
        }
    }

    /** Frees the vertex memory (disconnect, resource reload). / 释放顶点内存（断线、资源重载）。 */
    static void close() {
        discardFrame();
        if (allocator != null) {
            allocator.close();
            allocator = null;
        }
    }

    @Nullable
    private static Frame takeFrame() {
        Frame open = frame;
        frame = null;
        if (open != null) {
            EntityModelCapture.close(open);
        }
        return open;
    }

    private static void redrawBody(MinecraftClient client, AbstractClientPlayerEntity body, Vec3d camera,
                                   float tickDelta, VertexConsumerProvider provider) {
        try {
            EntityRedraw.draw(client.getEntityRenderDispatcher(), body, tickDelta, camera, provider,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE);
        } catch (RuntimeException exception) {
            // A third-party render hook failing here must not crash the frame; that body just has no silhouette.
            // 第三方渲染钩子在此出错时不能让整帧崩溃；该身体只是没有轮廓。
            if (!warned) {
                warned = true;
                SparkWitch.LOGGER.warn("Blind silhouette render failed for {}", body.getName().getString(),
                        exception);
            }
        }
    }

    private static BufferAllocator allocator() {
        if (allocator == null) {
            allocator = new BufferAllocator(INITIAL_BUFFER_BYTES);
        }
        return allocator;
    }

    /**
     * One frame's white buffer and the bodies it wants recorded from the world pass.
     * 一帧的白色缓冲，以及它想从世界 pass 录制的身体。
     */
    private static final class Frame implements EntityModelCapture.Recorder {
        private final BufferBuilder builder;
        private final WhiteVertexConsumer white;
        private final Set<Entity> wanted = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<Entity> recorded = Collections.newSetFromMap(new IdentityHashMap<>());

        Frame(List<AbstractClientPlayerEntity> bodies) {
            builder = new BufferBuilder(allocator(), VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            white = new WhiteVertexConsumer(builder);
            wanted.addAll(bodies);
        }

        @Override
        public boolean wants(Entity entity) {
            return wanted.contains(entity);
        }

        @Override
        public VertexConsumer sink() {
            return white;
        }

        @Override
        public void recorded(Entity entity) {
            recorded.add(entity);
        }

        /**
         * Closes the white consumer before the builder ends: a consumer or provider another mod kept is dead from
         * here on. 在构建器结束前关闭白色消费者：其他模组保留的消费者或提供器从此失效。
         */
        @Nullable
        BuiltBuffer end() {
            white.close();
            return builder.endNullable();
        }

        void discard() {
            BuiltBuffer built = end();
            if (built != null) {
                built.close();
            }
        }
    }

    /**
     * Keeps only positions and paints every vertex opaque white; texture, overlay, light and normal are ignored.
     * Valid for one frame only: other mods may keep the consumer or provider and write through it after the builder
     * ended (PatPat 1.3 draws its queued pat hand at the next frame's {@code WorldRenderEvents.AFTER_ENTITIES}), so
     * every write after {@link #close()} is dropped.
     * 只保留位置并把每个顶点涂成不透明白色；忽略纹理、覆盖层、光照与法线。仅在一帧内有效：其他模组可能保留消费者或
     * 提供器并在构建器结束后继续写入（PatPat 1.3 在下一帧 {@code WorldRenderEvents.AFTER_ENTITIES} 绘制排队的拍头之手），
     * 因此 {@link #close()} 之后的写入全部丢弃。
     */
    static final class WhiteVertexConsumer implements VertexConsumer {
        @Nullable
        private VertexConsumer delegate;

        WhiteVertexConsumer(VertexConsumer delegate) {
            this.delegate = delegate;
        }

        /** Ends this frame's writes; idempotent. / 结束本帧的写入；可重复调用。 */
        void close() {
            delegate = null;
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            VertexConsumer target = delegate;
            if (target != null) {
                target.vertex(x, y, z).color(255, 255, 255, 255);
            }
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }
    }
}
