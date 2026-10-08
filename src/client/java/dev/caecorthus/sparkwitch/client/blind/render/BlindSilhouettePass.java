package dev.caecorthus.sparkwitch.client.blind.render;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import dev.caecorthus.sparkwitch.SparkWitch;
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
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.List;

/**
 * Re-renders the perceived players' bodies, white and flat, into the private {@code blind_sil} target with its own
 * depth test, so the echo pass can show the parts a wall hides (D1). It uses the normal entity renderer path, so any
 * render gate on other players also applies; while it runs {@link #isRendering()} is true and
 * {@code BlindEchoSilhouetteMixin} drops feature renderers (held items, armor, capes) and name labels, so the shape
 * never shows a knife or gun. Render thread only; called during world rendering after the depth capture.
 * 把被感知玩家的身体以纯白重新渲染到私有 {@code blind_sil} 目标（自带深度测试），让回声 pass 能显示被墙挡住的部分（D1）。
 * 走普通实体渲染路径，因此针对其他玩家的渲染闸门同样生效；运行期间 {@link #isRendering()} 为真，
 * {@code BlindEchoSilhouetteMixin} 会去掉特征渲染器（手持物、盔甲、披风）与名字标签，轮廓不会露出刀或枪的形状。
 * 仅限渲染线程；在世界渲染中深度捕获之后调用。
 */
public final class BlindSilhouettePass {
    private static final int INITIAL_BUFFER_BYTES = 64 * 1024;
    private static boolean rendering;
    private static boolean warned;
    @Nullable
    private static BufferAllocator allocator;

    private BlindSilhouettePass() {
    }

    /** True only while the silhouette bodies are being emitted. / 仅在输出轮廓身体期间为真。 */
    public static boolean isRendering() {
        return rendering;
    }

    /**
     * Clears {@code target} and draws {@code bodies} into it, then re-binds {@code main}.
     * 清空 {@code target} 并把 {@code bodies} 画进去，然后重新绑定 {@code main}。
     */
    static void render(MinecraftClient client, Framebuffer target, Framebuffer main,
                       List<AbstractClientPlayerEntity> bodies, Matrix4f projection, Matrix4f viewRotation,
                       Vec3d camera, float tickDelta) {
        target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        target.clear(MinecraftClient.IS_SYSTEM_MAC);
        if (bodies.isEmpty()) {
            main.beginWrite(false);
            return;
        }
        BufferBuilder builder = new BufferBuilder(allocator(), VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_COLOR);
        WhiteVertexConsumer white = new WhiteVertexConsumer(builder);
        VertexConsumerProvider provider = layer -> white;
        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        rendering = true;
        try {
            for (AbstractClientPlayerEntity body : bodies) {
                emitBody(dispatcher, body, camera, tickDelta, provider);
            }
        } finally {
            rendering = false;
            // Closed before the builder ends: a provider another mod kept is dead from here on.
            // 在构建器结束前关闭：其他模组保留的提供器从此失效。
            white.close();
        }
        BuiltBuffer built = builder.endNullable();
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
        if (allocator != null) {
            allocator.close();
            allocator = null;
        }
    }

    private static void emitBody(EntityRenderDispatcher dispatcher, AbstractClientPlayerEntity body, Vec3d camera,
                                 float tickDelta, VertexConsumerProvider provider) {
        // Same placement as WorldRenderer#renderEntity + EntityRenderDispatcher#render, minus shadow, fire, hitbox.
        // 与 WorldRenderer#renderEntity + EntityRenderDispatcher#render 的摆放相同，但不画阴影、火焰与碰撞箱。
        double x = MathHelper.lerp(tickDelta, body.lastRenderX, body.getX()) - camera.x;
        double y = MathHelper.lerp(tickDelta, body.lastRenderY, body.getY()) - camera.y;
        double z = MathHelper.lerp(tickDelta, body.lastRenderZ, body.getZ()) - camera.z;
        float yaw = MathHelper.lerp(tickDelta, body.prevYaw, body.getYaw());
        MatrixStack matrices = new MatrixStack();
        try {
            EntityRenderer<? super AbstractClientPlayerEntity> renderer = dispatcher.getRenderer(body);
            Vec3d offset = renderer.getPositionOffset(body, tickDelta);
            matrices.translate(x + offset.x, y + offset.y, z + offset.z);
            renderer.render(body, yaw, tickDelta, matrices, provider, LightmapTextureManager.MAX_LIGHT_COORDINATE);
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
     * Keeps only positions and paints every vertex opaque white; texture, overlay, light and normal are ignored.
     * Valid for one pass only: other mods may keep the provider and write through it after the builder ended (PatPat
     * 1.3 draws its queued pat hand at the next frame's {@code WorldRenderEvents.AFTER_ENTITIES}), so every write after
     * {@link #close()} is dropped.
     * 只保留位置并把每个顶点涂成不透明白色；忽略纹理、覆盖层、光照与法线。仅在一次 pass 内有效：其他模组可能保留提供器并在
     * 构建器结束后继续写入（PatPat 1.3 在下一帧 {@code WorldRenderEvents.AFTER_ENTITIES} 绘制排队的拍头之手），
     * 因此 {@link #close()} 之后的写入全部丢弃。
     */
    static final class WhiteVertexConsumer implements VertexConsumer {
        @Nullable
        private VertexConsumer delegate;

        WhiteVertexConsumer(VertexConsumer delegate) {
            this.delegate = delegate;
        }

        /** Ends this pass's writes; idempotent. / 结束本次 pass 的写入；可重复调用。 */
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
