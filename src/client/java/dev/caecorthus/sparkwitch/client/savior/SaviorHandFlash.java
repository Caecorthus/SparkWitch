package dev.caecorthus.sparkwitch.client.savior;

import dev.caecorthus.sparkwitch.SparkWitch;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * First-person muzzle flash in hand space, copied from Wathe's revolver ({@code RevolverItem.spawnHandParticle()} plus
 * {@code HandParticleManager}). A world particle looks wrong in first person because the camera sits inside the model,
 * so Wathe renders this quad in the hand matrix instead; the additive render layer is copied from
 * {@code WatheRenderLayers.additive}. Spawned on every shot and rendered only while the first-person hand is drawn.
 * 第一人称枪口火焰，位于手的空间，复制自 Wathe 左轮（{@code RevolverItem.spawnHandParticle()} 与
 * {@code HandParticleManager}）。第一人称下镜头在模型内部，世界粒子看起来不对，所以 Wathe 改为在手部矩阵里绘制这个
 * 四边形；加色渲染层复制自 {@code WatheRenderLayers.additive}。每次开枪生成，只在绘制第一人称手部时渲染。
 */
public final class SaviorHandFlash {
    private static final Identifier TEXTURE = SparkWitch.id("textures/particle/gunshot.png");
    /** Wathe's hand-particle pose, fitted to the revolver-style first-person item display. / 与左轮式第一人称 display 对齐的手部位置。 */
    private static final float X = 0.1F;
    private static final float Y = 0.275F;
    private static final float Z = -0.2F;
    private static final float SIZE = 0.5F;
    /** Wathe's setMaxAge(3) adds one. / Wathe 的 setMaxAge(3) 会加一。 */
    private static final float MAX_AGE = 4.0F;
    private static final float ALPHA_START = 1.0F;
    private static final float ALPHA_END = 0.1F;
    /** Packed full-bright light, same as Wathe's hand flash. / 打包的全亮光照，与 Wathe 手部火焰相同。 */
    private static final int FULL_BRIGHT = 15_728_880;
    private static final RenderLayer LAYER = additive(TEXTURE);
    private static final List<SaviorHandFlash> ACTIVE = new CopyOnWriteArrayList<>();

    private float age;

    private SaviorHandFlash() {
    }

    /** One flash per shot; a newer shot starts its own fade. / 每次开枪一个火焰；新开枪会开启自己的淡出。 */
    public static void spawn() {
        if (ACTIVE.size() >= 8) {
            ACTIVE.remove(0);
        }
        ACTIVE.add(new SaviorHandFlash());
    }

    /** Client tick: advance and retire. / 客户端刻：推进并移除。 */
    public static void tick() {
        for (SaviorHandFlash flash : ACTIVE) {
            flash.age += 1.0F;
            if (flash.age >= MAX_AGE) {
                ACTIVE.remove(flash);
            }
        }
    }

    /** Draws the quads in the current hand matrix; called after the first-person item render. / 在当前手部矩阵中绘制四边形；第一人称物品渲染后调用。 */
    public static void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Matrix4f model = matrices.peek().getPositionMatrix();
        VertexConsumer consumer = vertexConsumers.getBuffer(LAYER);
        float half = SIZE * 0.5F;
        Vector3f center = new Vector3f(X, Y, Z);
        Vector3f right = new Vector3f(half, 0.0F, 0.0F);
        Vector3f up = new Vector3f(0.0F, half, 0.0F);
        for (SaviorHandFlash flash : ACTIVE) {
            float alpha = ALPHA_START + (flash.age / MAX_AGE) * (ALPHA_END - ALPHA_START);
            putVertex(consumer, model, center, right, up, -1.0F, -1.0F, 0.0F, 1.0F, alpha);
            putVertex(consumer, model, center, right, up, -1.0F, 1.0F, 0.0F, 0.0F, alpha);
            putVertex(consumer, model, center, right, up, 1.0F, 1.0F, 1.0F, 0.0F, alpha);
            putVertex(consumer, model, center, right, up, 1.0F, -1.0F, 1.0F, 1.0F, alpha);
        }
    }

    private static void putVertex(VertexConsumer consumer, Matrix4f model, Vector3f center, Vector3f right, Vector3f up,
                                  float dx, float dy, float u, float v, float alpha) {
        Vector3f pos = new Vector3f(center).add(new Vector3f(right).mul(dx)).add(new Vector3f(up).mul(dy));
        consumer.vertex(model, pos.x, pos.y, pos.z)
                .color(alpha, alpha, alpha, alpha)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(FULL_BRIGHT)
                .normal(0.0F, 1.0F, 0.0F);
    }

    /** Wathe's additive layer: bright flash without darkening the gun behind it. / Wathe 的加色层：火焰发亮且不会压暗后面的枪。 */
    private static RenderLayer additive(Identifier texture) {
        return RenderLayer.of(
                "sparkwitch_savior_hand_flash",
                VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                DrawMode.QUADS,
                256,
                true,
                true,
                RenderLayer.MultiPhaseParameters.builder()
                        .program(RenderPhase.ENTITY_TRANSLUCENT_PROGRAM)
                        .texture(new RenderPhase.Texture(texture, false, false))
                        .transparency(RenderPhase.ADDITIVE_TRANSPARENCY)
                        .depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
                        .cull(RenderPhase.DISABLE_CULLING)
                        .lightmap(RenderPhase.ENABLE_LIGHTMAP)
                        .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
                        .build(true));
    }
}
