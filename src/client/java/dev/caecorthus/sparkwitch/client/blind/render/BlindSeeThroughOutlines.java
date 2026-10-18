package dev.caecorthus.sparkwitch.client.blind.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

/**
 * Add-on see-through outlines behind {@code api.client.BlindViewApi}, drawn over the Blind's echo frame. The echo pass
 * overwrites the whole frame after world rendering, so outlines other mods draw at {@code WorldRenderEvents.LAST}
 * (SparkStrength's tripped breakers and engine units) never reach the Blind; {@link BlindEchoView} calls
 * {@link #render} right after a successful echo pass instead. All boxes are collected before any GL call. Render
 * thread only.
 * {@code api.client.BlindViewApi} 背后的附属模组透视描边，画在盲人的回声画面之上。回声 pass 会在世界渲染之后覆盖整帧，
 * 因此其他模组在 {@code WorldRenderEvents.LAST} 绘制的描边（SparkStrength 的跳闸电闸与动力装置）永远到不了盲人；
 * 改由 {@link BlindEchoView} 在回声 pass 成功后立即调用 {@link #render}。所有包围盒在任何 GL 调用之前收集完毕。
 * 仅限渲染线程。
 */
public final class BlindSeeThroughOutlines {
    /** Per-frame cap across every source. / 所有来源合计的每帧上限。 */
    static final int MAX_OUTLINES = 512;
    /** Outward margin so a box drawn on a block's faces never flickers. / 外扩余量，避免贴着方块表面的线闪烁。 */
    private static final double EXPAND = 0.02;
    private static final float LINE_WIDTH = 3.5F;
    private static final List<Consumer<ObjIntConsumer<Box>>> SOURCES = new CopyOnWriteArrayList<>();
    private static final List<Outline> FRAME = new ArrayList<>();
    private static final Sink SINK = new Sink();
    private static boolean warned;

    private BlindSeeThroughOutlines() {
    }

    /** One validated box and its {@code 0xAARRGGBB} colour. / 一个已校验的包围盒及其 {@code 0xAARRGGBB} 颜色。 */
    record Outline(Box box, int argb) {
    }

    public static void add(Consumer<ObjIntConsumer<Box>> source) {
        SOURCES.add(source);
    }

    /**
     * Clears {@code out} and fills it with this frame's valid boxes, at most {@link #MAX_OUTLINES}; returns the count.
     * A source that throws is dropped with one warning, and the boxes it reported this frame are discarded.
     * 清空 {@code out} 并填入本帧有效的包围盒（最多 {@link #MAX_OUTLINES} 个），返回数量。抛出异常的来源记录一次警告后被丢弃，
     * 它本帧报告的包围盒也一并作废。
     */
    static int collect(List<Outline> out) {
        out.clear();
        if (SOURCES.isEmpty()) {
            return 0;
        }
        SINK.target = out;
        try {
            for (Consumer<ObjIntConsumer<Box>> source : SOURCES) {
                if (out.size() >= MAX_OUTLINES) {
                    break;
                }
                int before = out.size();
                try {
                    source.accept(SINK);
                } catch (RuntimeException | LinkageError failure) {
                    SOURCES.remove(source);
                    out.subList(before, out.size()).clear();
                    SparkWitch.LOGGER.warn("Dropping a Blind see-through outline source that threw", failure);
                }
            }
        } finally {
            // A source that kept the sink can no longer write into a later frame.
            // 保留了接收器的来源无法再写入之后的帧。
            SINK.target = null;
        }
        return out.size();
    }

    /**
     * Draws this frame's outlines over the echo frame, camera-relative with the world's {@code projection} and
     * {@code viewRotation} captured for this frame. Expects the main framebuffer bound with its viewport and depth
     * test off; never throws, so it cannot break the echo view.
     * 以本帧捕获的世界 {@code projection} 与 {@code viewRotation}，按相机相对坐标把本帧描边画在回声画面上。要求主帧缓冲
     * 已绑定（含视口）且深度测试关闭；从不抛出异常，因此不会破坏回声画面。
     */
    static void render(Matrix4f projection, Matrix4f viewRotation, double cameraX, double cameraY, double cameraZ) {
        try {
            if (collect(FRAME) > 0) {
                draw(FRAME, projection, viewRotation, cameraX, cameraY, cameraZ);
            }
        } catch (RuntimeException exception) {
            if (!warned) {
                warned = true;
                SparkWitch.LOGGER.warn("Blind see-through outlines failed to draw; skipping them", exception);
            }
        } finally {
            FRAME.clear();
        }
    }

    private static void draw(List<Outline> frame, Matrix4f projection, Matrix4f viewRotation, double cameraX,
                             double cameraY, double cameraZ) {
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        MatrixStack identity = new MatrixStack();
        for (Outline outline : frame) {
            int argb = outline.argb();
            WorldRenderer.drawBox(identity, buffer,
                    outline.box().offset(-cameraX, -cameraY, -cameraZ).expand(EXPAND),
                    (argb >> 16 & 0xFF) / 255.0F, (argb >> 8 & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F,
                    (argb >>> 24) / 255.0F);
        }
        // endNullable: an empty builder must not be ended with end(), which throws.
        // endNullable：空的构建器不能用 end() 结束，否则会抛异常。
        BuiltBuffer built = buffer.endNullable();
        if (built == null) {
            return;
        }
        // After world rendering the RenderSystem matrices are no longer the world ones: load this frame's captured
        // projection and view rotation (camera-relative, like WorldRenderer), saving our own copies like
        // BlindSilhouettePass, and restore them for the GUI that follows.
        // 世界渲染结束后 RenderSystem 的矩阵已不是世界矩阵：载入本帧捕获的投影与视角旋转（与 WorldRenderer 一样按相机相对
        // 坐标），并像 BlindSilhouettePass 那样保存自己的副本，供随后的 GUI 恢复使用。
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorter savedSorting = RenderSystem.getVertexSorting();
        float savedFogStart = RenderSystem.getShaderFogStart();
        float savedLineWidth = RenderSystem.getShaderLineWidth();
        float[] shaderColor = RenderSystem.getShaderColor();
        float red = shaderColor[0];
        float green = shaderColor[1];
        float blue = shaderColor[2];
        float alpha = shaderColor[3];
        modelView.pushMatrix();
        try {
            RenderSystem.setProjectionMatrix(projection, VertexSorter.BY_DISTANCE);
            modelView.set(viewRotation);
            RenderSystem.applyModelViewMatrix();
            // Fog-free and see-through: walls and the Blind's world fog never hide the outline.
            // 无雾且透视：墙体与盲人的世界雾都挡不住描边。
            RenderSystem.setShaderFogStart(Float.MAX_VALUE);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            // rendertype_lines expands each line into a screen-space quad, so the width holds on core-profile GL
            // (macOS), where plain GL lines are always one pixel.
            // rendertype_lines 把每条线展开为屏幕空间四边形，因此在核心模式 GL（macOS）下宽度依然有效；普通 GL 线在那里只有一像素。
            RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
            RenderSystem.lineWidth(LINE_WIDTH);
            BufferRenderer.drawWithGlobalProgram(built);
        } finally {
            RenderSystem.lineWidth(savedLineWidth);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.setShaderColor(red, green, blue, alpha);
            RenderSystem.setShaderFogStart(savedFogStart);
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
        }
    }

    /**
     * The sink handed to sources: validates each box and writes only while {@link #collect} runs.
     * 交给来源的接收器：逐个校验包围盒，且只在 {@link #collect} 运行期间写入。
     */
    private static final class Sink implements ObjIntConsumer<Box> {
        @Nullable
        private List<Outline> target;

        @Override
        public void accept(@Nullable Box box, int argb) {
            List<Outline> out = target;
            if (out == null || out.size() >= MAX_OUTLINES || box == null || argb >>> 24 == 0 || !isFinite(box)) {
                return;
            }
            out.add(new Outline(box, argb));
        }

        private static boolean isFinite(Box box) {
            return Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ)
                    && Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ);
        }
    }
}
