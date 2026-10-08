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
 * Draws the scope glint (S1, WP6 mockup) at every other scoped USEC's scope objective: a camera-facing billboard of a
 * small generated flare (premultiplied {@code #FFF6DE} core and streaks, {@code #FFE2AA} halo), screen-blended in
 * {@code WorldRenderEvents.AFTER_TRANSLUCENT}. It reads only the all-player synced {@code UsecPlayerComponent.scoped}
 * flag, which the server sets after its own checks, and the USEC's synced head yaw and pitch. Occlusion is a point
 * test like the mockup's: a client block raycast from the camera to the objective with VISUAL shapes (clear glass and
 * windows let it through), so walls hide it while the flare itself is never clipped by the USEC's own head or rifle.
 * It deliberately draws in both world passes of a Picture-in-Picture frame, sized from
 * {@code ScopeClient.screenPixelsPerNdcY}: at 1x in the main pass (seen outside the lens) and magnified in the lens pass,
 * exactly as large as Full-Screen Zoom shows it. The composite replaces the main picture inside the lens, so the two
 * never stack. Presentation only.
 * 在其他每个开镜 USEC 的物镜处绘制镜头反光（S1，WP6 样稿）：一个朝向镜头的小型生成闪光公告板（预乘的 {@code #FFF6DE}
 * 核心与光芒、{@code #FFE2AA} 光晕），在 {@code WorldRenderEvents.AFTER_TRANSLUCENT} 中以滤色混合绘制。只读取同步给全员的
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
            Vec3d scope = objective(player, tickDelta);
            Vec3d look = player.getRotationVec(tickDelta);
            Vec3d toViewer = cameraPos.subtract(scope);
            double intensity = UsecGlintRules.intensity(UsecGlintRules.aimCosine(look.x, look.y, look.z,
                    toViewer.x, toViewer.y, toViewer.z));
            if (intensity <= 0.0 || occluded(world, cameraPos, scope)) {
                continue;
            }
            double distance = toViewer.length();
            double halfPixels = UsecGlintRules.spriteHalfPixels(
                    UsecGlintRules.corePixels(distance, pixelsPerTangent, heightPx), heightPx);
            double half = UsecGlintRules.worldHalfSize(halfPixels, distance, pixelsPerTangent);
            double alpha = UsecGlintRules.alpha(intensity, world.getTime() + tickDelta, player.getId());
            if (!(half > 0.0)) {
                continue;
            }
            if (buffer == null) {
                buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,
                        VertexFormats.POSITION_TEXTURE_COLOR);
            }
            quad(buffer, context.positionMatrix(), camera, scope.subtract(cameraPos), (float) half,
                    (int) Math.round(alpha * 255.0));
        }
        if (buffer != null) {
            draw(buffer);
        }
    }

    /**
     * Scope objective: eye + 0.55 along the aim, 0.09 to the right, 0.06 up. MC yaw 0 faces +Z, so right is
     * {@code (-cos yaw, 0, -sin yaw)}.
     * 物镜位置：眼睛沿瞄准方向 0.55、向右 0.09、向上 0.06。MC 偏航 0 朝向 +Z，因此右方为 {@code (-cos yaw, 0, -sin yaw)}。
     */
    static Vec3d objective(AbstractClientPlayerEntity player, float tickDelta) {
        Vec3d eye = player.getCameraPosVec(tickDelta);
        Vec3d look = player.getRotationVec(tickDelta);
        double yaw = Math.toRadians(player.getYaw(tickDelta));
        return eye.add(look.multiply(UsecGlintRules.SCOPE_FORWARD))
                .add(-Math.cos(yaw) * UsecGlintRules.SCOPE_RIGHT, UsecGlintRules.SCOPE_UP,
                        -Math.sin(yaw) * UsecGlintRules.SCOPE_RIGHT);
    }

    private static boolean occluded(ClientWorld world, Vec3d from, Vec3d to) {
        HitResult hit = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.VISUAL,
                RaycastContext.FluidHandling.NONE, ShapeContext.absent()));
        return hit != null && hit.getType() != HitResult.Type.MISS;
    }

    private static void quad(BufferBuilder buffer, Matrix4f view, Camera camera, Vec3d offset, float half,
                             int alpha) {
        MatrixStack stack = new MatrixStack();
        stack.multiplyPositionMatrix(view);
        stack.translate(offset.x, offset.y, offset.z);
        stack.multiply(camera.getRotation());
        stack.scale(half, half, half);
        Matrix4f matrix = stack.peek().getPositionMatrix();
        // Premultiplied texture: the intensity scales colour and alpha alike. / 预乘贴图：强度同时缩放颜色与透明度。
        buffer.vertex(matrix, -1.0F, -1.0F, 0.0F).texture(0.0F, 1.0F).color(alpha, alpha, alpha, alpha);
        buffer.vertex(matrix, -1.0F, 1.0F, 0.0F).texture(0.0F, 0.0F).color(alpha, alpha, alpha, alpha);
        buffer.vertex(matrix, 1.0F, 1.0F, 0.0F).texture(1.0F, 0.0F).color(alpha, alpha, alpha, alpha);
        buffer.vertex(matrix, 1.0F, -1.0F, 0.0F).texture(1.0F, 1.0F).color(alpha, alpha, alpha, alpha);
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
