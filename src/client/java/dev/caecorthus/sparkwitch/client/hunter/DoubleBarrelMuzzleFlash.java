package dev.caecorthus.sparkwitch.client.hunter;

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
 * First-person muzzle flash for the double-barrel shotgun, rendered in hand space like Wathe's revolver flash. The
 * texture is Wathe's yellow {@code gunshot.png}, not the Savior's cyan copy. The position is Wathe's revolver hand
 * pose (0.1, 0.275, -0.2) shifted by the shotgun sprite's muzzle offset: the barrel tip sits 2 px left and 1 px up of
 * the revolver's in the 16x16 texture, which maps to (0, -0.0265, -0.0796) in hand space under the shared
 * first-person display transform.
 * 双管猎枪的第一人称枪口火焰，与 Wathe 左轮一样在手部矩阵中绘制。贴图使用 Wathe 的黄色
 * {@code gunshot.png}，不是救世主的青色副本。位置以 Wathe 左轮的手部姿态 (0.1, 0.275, -0.2) 为基准，加上猎枪
 * 贴图的枪口偏移：16x16 贴图中猎枪枪口比左轮靠左 2 像素、靠上 1 像素，在共用的第一人称 display 变换下对应手部
 * 空间 (0, -0.0265, -0.0796)。
 */
public final class DoubleBarrelMuzzleFlash {
    private static final Identifier TEXTURE = Identifier.of("wathe", "textures/particle/gunshot.png");
    /** Wathe revolver pose plus the shotgun's muzzle delta. / Wathe 左轮姿态加上猎枪枪口偏移。 */
    private static final float X = 0.1F;
    private static final float Y = 0.2485F;
    private static final float Z = -0.2796F;
    private static final float SIZE = 0.5F;
    /** Wathe's setMaxAge(3) adds one. / Wathe 的 setMaxAge(3) 会加一。 */
    private static final float MAX_AGE = 4.0F;
    private static final float ALPHA_START = 1.0F;
    private static final float ALPHA_END = 0.1F;
    /** Packed full-bright light, same as Wathe's hand flash. / 打包的全亮光照，与 Wathe 手部火焰相同。 */
    private static final int FULL_BRIGHT = 15_728_880;
    private static final RenderLayer LAYER = additive(TEXTURE);
    private static final List<DoubleBarrelMuzzleFlash> ACTIVE = new CopyOnWriteArrayList<>();

    private float age;

    private DoubleBarrelMuzzleFlash() {
    }

    /** One flash per shot; a newer shot starts its own fade. / 每次开枪一个火焰；新开枪会开启自己的淡出。 */
    public static void spawn() {
        if (ACTIVE.size() >= 8) {
            ACTIVE.remove(0);
        }
        ACTIVE.add(new DoubleBarrelMuzzleFlash());
    }

    /** Client tick: advance and retire. / 客户端刻：推进并移除。 */
    public static void tick() {
        for (DoubleBarrelMuzzleFlash flash : ACTIVE) {
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
        for (DoubleBarrelMuzzleFlash flash : ACTIVE) {
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
                "sparkwitch_double_barrel_hand_flash",
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
