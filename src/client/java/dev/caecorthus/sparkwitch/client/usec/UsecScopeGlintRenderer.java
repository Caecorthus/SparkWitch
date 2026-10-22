package dev.caecorthus.sparkwitch.client.usec;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.blind.gate.BlindClientGates;
import dev.caecorthus.sparkwitch.client.render.WraithClientState;
import dev.caecorthus.sparkwitch.client.scope.ScopeClient;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecPlayerComponent;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * Draws the scope glint (S1, WP6 mockup) on every other scoped USEC's objective lens, at the point where this pass
 * actually drew it ({@code UsecScopeLensAnchors}), so it rides the rifle's bob. Three camera-facing quads from one
 * premultiplied atlas ({@link UsecGlintRules.Cell}): a white-hot core the size of the lens glass with a small warm
 * halo, a thin screen-horizontal streak, and a vertical spike that the anamorphic look leaves empty. They share one
 * buffer and are screen-blended in {@code WorldRenderEvents.AFTER_TRANSLUCENT}. It reads only the all-player synced
 * {@code UsecPlayerComponent.scoped} flag, which the server sets after its own checks, and the USEC's synced head yaw
 * and pitch. Occlusion is a point
 * test like the mockup's: a client block raycast from the camera to the objective with VISUAL shapes (clear glass and
 * windows let it through), so walls hide it while the flare itself is never clipped by the USEC's own head or rifle.
 * It deliberately draws in both world passes of a Picture-in-Picture frame, sized from
 * {@code ScopeClient.screenPixelsPerNdcY}: at 1x in the main pass (seen outside the lens) and magnified in the lens pass,
 * exactly as large as Full-Screen Zoom shows it. The composite replaces the main picture inside the lens, so the two
 * never stack. Presentation only.
 * 在其他每个开镜 USEC 的物镜上、即本次渲染实际画出物镜的位置（{@code UsecScopeLensAnchors}）绘制镜头反光（S1，WP6 样稿），
 * 因此它随步枪一起晃动。一张预乘图集（{@link UsecGlintRules.Cell}）上的三个朝向镜头的四边形：与物镜玻璃一样大的白热
 * 核心加小暖色光晕、一条细的屏幕水平光芒，以及变形宽银幕外观中为空的竖直光芒。它们共用一个缓冲，在
 * {@code WorldRenderEvents.AFTER_TRANSLUCENT} 中以滤色混合绘制。只读取同步给全员的
 * {@code UsecPlayerComponent.scoped} 标记（服务端检查后设置）以及 USEC 同步的头部偏航与俯仰。遮挡与样稿一样是点测试：
 * 客户端以 VISUAL 形状从镜头向物镜做方块射线检测（透明玻璃与车窗可透过），因此墙会挡住它，而闪光本身绝不会被 USEC 自己
 * 的头或枪截断。画中画帧的两次世界渲染中都有意绘制，大小取自 {@code ScopeClient.screenPixelsPerNdcY}：主渲染中为 1 倍
 * （镜外可见），镜内渲染中放大，与全画面放大所见一样大。合成在镜内替换主画面，因此两者不会叠加。仅为表现。
 */
public final class UsecScopeGlintRenderer {
    public static final Identifier TEXTURE = SparkWitch.id("textures/entity/usec_scope_glint.png");

    private UsecScopeGlintRenderer() {
    }

    static void render(WorldRenderContext context) {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = context.world();
        if (world == null || client.player == null) {
            return;
        }
        // The Blind's world is echo line art: no light may leak into it. / 盲人的世界是回声线稿：不得泄露任何光。
        boolean blindView = BlindClientGates.viewActive();
        Camera camera = context.camera();
        Vec3d cameraPos = camera.getPos();
        float tickDelta = context.tickCounter().getTickDelta(false);
        // On-screen pixels per unit tangent of this pass's projection (m11 = 1 / tan(fov / 2)). The PiP lens pass
        // renders into a square sized by the player's Lens Resolution with a narrowed projection; the scope module maps
        // that back to screen pixels, so the flare in the lens matches Full-Screen Zoom and the 1x main pass stays as
        // before.
        // 本次渲染投影每单位正切对应的屏幕像素（m11 = 1 / tan(fov / 2)）。画中画镜内渲染输出到按玩家镜内分辨率确定大小的方形
        // 目标且投影已收窄；开镜模块把它换算回屏幕像素，因此镜内闪光与全画面放大一致，1 倍主渲染保持不变。
        double widthPx = client.getWindow().getFramebufferWidth();
        double heightPx = client.getWindow().getFramebufferHeight();
        double pixelsPerTangent = ScopeClient.screenPixelsPerNdcY(widthPx, heightPx)
                * context.projectionMatrix().m11();
        Entity cameraEntity = client.getCameraEntity();
        BufferBuilder buffer = null;
        for (AbstractClientPlayerEntity player : world.getPlayers()) {
            UsecPlayerComponent component = UsecPlayerComponent.KEY.getNullable(player);
            boolean scoped = component != null && component.isScoped();
            if (!scoped || !UsecGlintRules.draws(true, blindView, true, player == client.player,
                    player == cameraEntity, player.isInvisibleTo(client.player) || WraithClientState.isActive(player),
                    player.isSpectator()) || BlindClientGates.hidesEntity(player)) {
                continue;
            }
            // Only where the rifle was drawn in this pass. / 只在本次渲染画出步枪的位置。
            Vec3d scope = UsecScopeLensAnchors.lens(player);
            if (scope == null) {
                continue;
            }
            Vec3d look = player.getRotationVec(tickDelta);
            Vec3d toViewer = cameraPos.subtract(scope);
            double intensity = UsecGlintRules.intensity(UsecGlintRules.aimCosine(look.x, look.y, look.z,
                    toViewer.x, toViewer.y, toViewer.z));
            if (intensity <= 0.0 || occluded(world, cameraPos, scope)) {
                continue;
            }
            double distance = toViewer.length();
            double age = world.getTime() + tickDelta;
            double core = UsecGlintRules.corePixels(distance, pixelsPerTangent, heightPx);
            double rayPixels = UsecGlintRules.rayHalfLengthPixels(distance, pixelsPerTangent, heightPx)
                    * UsecGlintRules.rayScale(age, player.getId());
            float flare = (float) UsecGlintRules.worldHalfSize(UsecGlintRules.flareHalfPixels(core), distance,
                    pixelsPerTangent);
            float ray = (float) UsecGlintRules.worldHalfSize(rayPixels, distance, pixelsPerTangent);
            float thickness = (float) UsecGlintRules.worldHalfSize(UsecGlintRules.rayHalfThicknessPixels(rayPixels),
                    distance, pixelsPerTangent);
            int shade = (int) Math.round(UsecGlintRules.brightness(intensity, age, player.getId()) * 255.0);
            if (!(flare > 0.0F) || !(ray > 0.0F) || shade <= 0) {
                continue;
            }
            if (buffer == null) {
                buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,
                        VertexFormats.POSITION_TEXTURE_COLOR);
            }
            // Three quads from one atlas, in the same buffer: the round flare, the screen-horizontal streak and the
            // screen-vertical spike (empty in the anamorphic look).
            // 同一图集、同一缓冲中的三个四边形：圆形闪光、屏幕水平光芒与屏幕竖直光芒（变形宽银幕外观中为空）。
            Matrix4f billboard = billboard(context.positionMatrix(), camera, scope.subtract(cameraPos));
            quad(buffer, billboard, flare, flare, UsecGlintRules.Cell.FLARE, false, shade);
            quad(buffer, billboard, ray, thickness, UsecGlintRules.Cell.STREAK, false, shade);
            quad(buffer, billboard, thickness, ray, UsecGlintRules.Cell.SPIKE, true, shade);
        }
        if (buffer != null) {
            draw(buffer);
        }
    }

    private static boolean occluded(ClientWorld world, Vec3d from, Vec3d to) {
        HitResult hit = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.VISUAL,
                RaycastContext.FluidHandling.NONE, ShapeContext.absent()));
        return hit != null && hit.getType() != HitResult.Type.MISS;
    }

    /**
     * A camera-facing frame at the lens: local x is the camera's right (screen-horizontal), local y its up.
     * 物镜处朝向镜头的坐标系：局部 x 为镜头的右方向（屏幕水平），局部 y 为其上方向。
     */
    private static Matrix4f billboard(Matrix4f view, Camera camera, Vec3d offset) {
        MatrixStack stack = new MatrixStack();
        stack.multiplyPositionMatrix(view);
        stack.translate(offset.x, offset.y, offset.z);
        stack.multiply(camera.getRotation());
        return stack.peek().getPositionMatrix();
    }

    /**
     * One quad of {@code halfX} x {@code halfY} blocks textured with an atlas cell. An upright quad turns the cell a
     * quarter: its u runs down the quad, so the lying spike cell stands up.
     * 一个 {@code halfX} x {@code halfY} 格、贴图集格子的四边形。竖直四边形把格子转四分之一圈：u 沿四边形向下，因此横放
     * 存储的竖直光芒格子会立起来。
     */
    private static void quad(BufferBuilder buffer, Matrix4f matrix, float halfX, float halfY, UsecGlintRules.Cell cell,
                             boolean upright, int shade) {
        corner(buffer, matrix, -halfX, -halfY, cell, upright, 0.0F, 1.0F, shade);
        corner(buffer, matrix, -halfX, halfY, cell, upright, 0.0F, 0.0F, shade);
        corner(buffer, matrix, halfX, halfY, cell, upright, 1.0F, 0.0F, shade);
        corner(buffer, matrix, halfX, -halfY, cell, upright, 1.0F, 1.0F, shade);
    }

    private static void corner(BufferBuilder buffer, Matrix4f matrix, float x, float y, UsecGlintRules.Cell cell,
                               boolean upright, float across, float down, int shade) {
        float u = cell.u0 + (cell.u1 - cell.u0) * (upright ? down : across);
        float v = cell.v0 + (cell.v1 - cell.v0) * (upright ? across : down);
        // Premultiplied texture: the brightness scales the colour; alpha stays opaque so the core shader's alpha < 0.1
        // discard keeps to the texture's own black edge. / 预乘贴图：亮度缩放颜色；透明度保持不透明，使核心着色器
        // alpha < 0.1 的丢弃只落在贴图自身的黑色边缘。
        buffer.vertex(matrix, x, y, 0.0F).texture(u, v).color(shade, shade, shade, 255);
    }

    /**
     * Screen blend ({@code 1 - (1 - dst)(1 - src)}, the mockup's), no depth test or write, no culling; the model-view
     * is reset because AFTER_TRANSLUCENT carries no camera transform (the vertices already include it). Every touched
     * state is restored.
     * 滤色混合（{@code 1 - (1 - dst)(1 - src)}，与样稿一致），不做深度测试与写入，不剔除；由于 AFTER_TRANSLUCENT 不包含
     * 镜头变换（顶点已包含），模型视图被重置。所有改动的状态都会恢复。
     */
    private static void draw(BufferBuilder buffer) {
        BuiltBuffer built = buffer.endNullable();
        if (built == null) {
            return;
        }
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.ONE_MINUS_DST_COLOR, GlStateManager.DstFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        BufferRenderer.drawWithGlobalProgram(built);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }
}
