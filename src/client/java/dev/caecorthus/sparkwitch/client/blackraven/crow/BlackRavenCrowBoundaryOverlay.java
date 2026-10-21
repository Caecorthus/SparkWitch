package dev.caecorthus.sparkwitch.client.blackraven.crow;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowEntity;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowFlightRules;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.border.WorldBorder;
import org.joml.Matrix4f;

/**
 * Screen-edge boundary indicator while the local Raven flies its crow (owner Q10). It reads Wathe's play area, synced to
 * every client, and the world border, the same clamp box the server enforces; the geometry is
 * {@link BlackRavenCrowBoundaryRules} (horizontal faces within 8 blocks; floor and ceiling within 2 blocks, only while
 * the pilot's commanded flight heads toward them). Everything is drawn with fills (no textures): edge glows with a
 * gradient into the screen, an arrow toward the nearest warning face and its distance. Presentation only.
 * 本地黑羽鸦操控乌鸦时的屏幕边缘边界提示（所有者 Q10）。读取同步给所有客户端的 Wathe 游戏区域与世界边界，与服务端执行的限制框相同；
 * 几何计算见 {@link BlackRavenCrowBoundaryRules}（水平面 8 格以内；底面与顶面 2 格以内，且仅在驾驶指令朝其飞行时）。全部以填充
 * 绘制（不用贴图）：向屏幕内渐变的边缘泛光、指向最近警告边界面的箭头及其距离。仅用于展示。
 */
public final class BlackRavenCrowBoundaryOverlay {
    /** Warm red glow, readable over the night-vision view. / 暖红色泛光，在夜视画面上清晰可见。 */
    private static final int GLOW_RGB = 0xD8433A;
    private static final float GLOW_MAX_ALPHA = 0.6F;
    private static final float GLOW_DEPTH_FRACTION = 0.16F;
    private static final double ARROW_INSET = 26.0;
    private static final float ARROW_LENGTH = 7.0F;
    private static final float ARROW_HALF_WIDTH = 5.0F;
    private static final int TEXT_COLOR = 0xFFF2E6E4;

    private BlackRavenCrowBoundaryOverlay() {
    }

    /** {@code HudRenderCallback}. */
    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!BlackRavenCrowPilotClient.isViewingCrow()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        BlackRavenCrowEntity crow = BlackRavenCrowPilotClient.boundCrow();
        if (crow == null || client.world == null) {
            return;
        }
        WorldBorder border = client.world.getWorldBorder();
        Box bounds = BlackRavenCrowFlightRules.bounds(MapVariablesWorldComponent.KEY.get(client.world).getPlayArea(),
                border.getBoundWest(), border.getBoundNorth(), border.getBoundEast(), border.getBoundSouth());
        Box hull = crow.getBoundingBox();
        // Floor and ceiling warn only while the crow heads toward them. / 底面与顶面只在乌鸦朝其移动时警告。
        Vec3d motion = BlackRavenCrowPilotClient.commandedVelocity();
        BlackRavenCrowBoundaryRules.FaceDistance nearest = BlackRavenCrowBoundaryRules.nearest(hull, bounds, motion);
        if (nearest == null) {
            return;
        }
        Camera camera = client.gameRenderer.getCamera();
        BlackRavenCrowBoundaryRules.Basis basis = BlackRavenCrowBoundaryRules.basis(camera.getYaw(), camera.getPitch());
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        renderGlow(context, BlackRavenCrowBoundaryRules.edgeGlow(hull, bounds, basis, motion), width, height);
        float strength = BlackRavenCrowBoundaryRules.intensity(nearest.face(), nearest.distance(), motion);
        BlackRavenCrowBoundaryRules.Arrow arrow = BlackRavenCrowBoundaryRules.arrow(
                nearest.face().normal(), basis, width, height, ARROW_INSET);
        renderArrow(context, arrow, strength);
        TextRenderer renderer = client.textRenderer;
        Text label = Text.translatable("hud.sparkwitch.black_raven_crow.boundary",
                BlackRavenCrowBoundaryRules.formatDistance(nearest.distance()));
        // Label on the screen-centre side of the arrow. / 标签放在箭头朝屏幕中心的一侧。
        double towardCenterX = -Math.cos(arrow.angle());
        double towardCenterY = -Math.sin(arrow.angle());
        int labelX = (int) Math.round(arrow.x() + towardCenterX * 16.0 - renderer.getWidth(label) / 2.0);
        int labelY = (int) Math.round(arrow.y() + towardCenterY * 16.0 - renderer.fontHeight / 2.0);
        labelX = Math.max(2, Math.min(width - renderer.getWidth(label) - 2, labelX));
        labelY = Math.max(2, Math.min(height - renderer.fontHeight - 2, labelY));
        context.drawTextWithShadow(renderer, label, labelX, labelY, TEXT_COLOR);
    }

    private static void renderGlow(DrawContext context, BlackRavenCrowBoundaryRules.EdgeGlow glow, int width,
                                   int height) {
        if (!glow.any()) {
            return;
        }
        float depth = Math.min(width, height) * GLOW_DEPTH_FRACTION;
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        VertexConsumer buffer = context.getVertexConsumers().getBuffer(RenderLayer.getGui());
        int clear = GLOW_RGB;
        if (glow.left() > 0.0F) {
            int edge = color(glow.left());
            quad(buffer, matrix, 0.0F, 0.0F, depth, height, edge, edge, clear, clear);
        }
        if (glow.right() > 0.0F) {
            int edge = color(glow.right());
            quad(buffer, matrix, width - depth, 0.0F, width, height, clear, clear, edge, edge);
        }
        if (glow.top() > 0.0F) {
            int edge = color(glow.top());
            quad(buffer, matrix, 0.0F, 0.0F, width, depth, edge, clear, clear, edge);
        }
        if (glow.bottom() > 0.0F) {
            int edge = color(glow.bottom());
            quad(buffer, matrix, 0.0F, height - depth, width, height, clear, edge, edge, clear);
        }
        context.draw();
    }

    private static void renderArrow(DrawContext context, BlackRavenCrowBoundaryRules.Arrow arrow, float strength) {
        float alpha = 0.55F + 0.45F * strength;
        int fill = ((int) (alpha * 255.0F) << 24) | GLOW_RGB;
        float cos = (float) Math.cos(arrow.angle());
        float sin = (float) Math.sin(arrow.angle());
        float cx = (float) arrow.x();
        float cy = (float) arrow.y();
        // Tip ahead, two base corners behind; one degenerate quad. / 尖端在前、两底角在后；一个退化四边形。
        float tipX = cx + cos * ARROW_LENGTH;
        float tipY = cy + sin * ARROW_LENGTH;
        float baseX = cx - cos * ARROW_LENGTH * 0.6F;
        float baseY = cy - sin * ARROW_LENGTH * 0.6F;
        float leftX = baseX - sin * ARROW_HALF_WIDTH;
        float leftY = baseY + cos * ARROW_HALF_WIDTH;
        float rightX = baseX + sin * ARROW_HALF_WIDTH;
        float rightY = baseY - cos * ARROW_HALF_WIDTH;
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        VertexConsumer buffer = context.getVertexConsumers().getBuffer(RenderLayer.getGui());
        buffer.vertex(matrix, tipX, tipY, 0.0F).color(fill);
        buffer.vertex(matrix, rightX, rightY, 0.0F).color(fill);
        buffer.vertex(matrix, leftX, leftY, 0.0F).color(fill);
        buffer.vertex(matrix, tipX, tipY, 0.0F).color(fill);
        // Same quad with the opposite winding, so either facing draws. / 反向绕序再画一次，任一朝向都可见。
        buffer.vertex(matrix, tipX, tipY, 0.0F).color(fill);
        buffer.vertex(matrix, leftX, leftY, 0.0F).color(fill);
        buffer.vertex(matrix, rightX, rightY, 0.0F).color(fill);
        buffer.vertex(matrix, tipX, tipY, 0.0F).color(fill);
        context.draw();
    }

    /**
     * One GUI quad with per-corner colours: top-left, bottom-left, bottom-right, top-right ({@code DrawContext.fill}'s
     * winding). / 一个四角分别着色的 GUI 四边形：左上、左下、右下、右上（与 DrawContext.fill 相同的绕序）。
     */
    private static void quad(VertexConsumer buffer, Matrix4f matrix, float x0, float y0, float x1, float y1,
                             int topLeft, int bottomLeft, int bottomRight, int topRight) {
        buffer.vertex(matrix, x0, y0, 0.0F).color(topLeft);
        buffer.vertex(matrix, x0, y1, 0.0F).color(bottomLeft);
        buffer.vertex(matrix, x1, y1, 0.0F).color(bottomRight);
        buffer.vertex(matrix, x1, y0, 0.0F).color(topRight);
    }

    private static int color(float intensity) {
        int alpha = Math.round(Math.max(0.0F, Math.min(1.0F, intensity)) * GLOW_MAX_ALPHA * 255.0F);
        return (alpha << 24) | GLOW_RGB;
    }
}
