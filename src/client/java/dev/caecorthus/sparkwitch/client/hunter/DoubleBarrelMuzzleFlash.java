package dev.caecorthus.sparkwitch.client.hunter;

import dev.doctor4t.wathe.client.render.WatheRenderLayers;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/**
 * First-person muzzle flash for the double-barrel shotgun: Wathe's revolver hand flash (yellow {@code gunshot.png},
 * Wathe's additive layer, size 0.5, 4 ticks, alpha 1 to 0.1, full bright) drawn only in the hand that renders the
 * shotgun. Wathe's own hand-particle manager draws in every first-person hand with any item, which would put a
 * second flash on whatever the other hand holds. The position is the revolver pose (0.1, 0.275, -0.2) shifted by the
 * shotgun sprite's muzzle offset: the barrel tip sits 2 px left and 1 px up of the revolver's in the 16x16 texture,
 * which maps to (0, -0.0265, -0.0796) in hand space under the shared first-person display transform. The left hand
 * uses the same rotation with the display translation x (1.13/16) negated, so its muzzle sits 2 x 1.13/16 further to
 * the left.
 * 双管猎枪的第一人称枪口火焰：即 Wathe 左轮的手部火焰（黄色 {@code gunshot.png}、Wathe 加色层、尺寸 0.5、4 刻、透明度
 * 1 到 0.1、全亮），但只画在渲染猎枪的那只手上。Wathe 自己的手部粒子管理器会在每只持物的第一人称手上都绘制，另一只手持物时
 * 会多出一团火。位置以左轮姿态 (0.1, 0.275, -0.2) 为基准，加上猎枪贴图的枪口偏移：16x16 贴图中猎枪枪口比左轮靠左 2 像素、
 * 靠上 1 像素，在共用的第一人称 display 变换下对应手部空间 (0, -0.0265, -0.0796)。左手旋转相同，但 display 平移 x
 * （1.13/16）取反，因此枪口再向左移 2 × 1.13/16。
 */
public final class DoubleBarrelMuzzleFlash {
    private static final Identifier TEXTURE = Identifier.of("wathe", "textures/particle/gunshot.png");
    /** Wathe revolver pose plus the shotgun's muzzle delta. / Wathe 左轮姿态加上猎枪枪口偏移。 */
    private static final float RIGHT_X = 0.1F;
    private static final float LEFT_X = RIGHT_X - 2.0F * 1.13F / 16.0F;
    private static final float Y = 0.2485F;
    private static final float Z = -0.2796F;
    private static final float HALF_SIZE = 0.25F;
    /** Wathe's setMaxAge(3) adds one. / Wathe 的 setMaxAge(3) 会加一。 */
    private static final float MAX_AGE = 4.0F;
    private static final float ALPHA_START = 1.0F;
    private static final float ALPHA_END = 0.1F;
    /** Wathe's setLight(15, 15). / 即 Wathe 的 setLight(15, 15)。 */
    private static final int FULL_BRIGHT = 15_728_880;
    private static final int MAX_ACTIVE = 8;
    private static final RenderLayer LAYER = WatheRenderLayers.additive(TEXTURE);
    private static final List<DoubleBarrelMuzzleFlash> ACTIVE = new CopyOnWriteArrayList<>();

    private float age;

    private DoubleBarrelMuzzleFlash() {
    }

    /** One flash per shot; a newer shot starts its own fade. / 每次开枪一个火焰；新开枪会开启自己的淡出。 */
    public static void spawn() {
        if (ACTIVE.size() >= MAX_ACTIVE) {
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

    /** Drops every flash (disconnect). / 清空所有火焰（断开连接）。 */
    public static void reset() {
        ACTIVE.clear();
    }

    /**
     * Draws the quads in the current first-person hand matrix, right after the shotgun item itself.
     * 在当前第一人称手部矩阵中绘制四边形，紧接在猎枪物品本身之后。
     */
    public static void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, boolean leftHanded) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Matrix4f model = matrices.peek().getPositionMatrix();
        VertexConsumer consumer = vertexConsumers.getBuffer(LAYER);
        float x = leftHanded ? LEFT_X : RIGHT_X;
        for (DoubleBarrelMuzzleFlash flash : ACTIVE) {
            float alpha = ALPHA_START + (flash.age / MAX_AGE) * (ALPHA_END - ALPHA_START);
            putVertex(consumer, model, x - HALF_SIZE, Y - HALF_SIZE, 0.0F, 1.0F, alpha);
            putVertex(consumer, model, x - HALF_SIZE, Y + HALF_SIZE, 0.0F, 0.0F, alpha);
            putVertex(consumer, model, x + HALF_SIZE, Y + HALF_SIZE, 1.0F, 0.0F, alpha);
            putVertex(consumer, model, x + HALF_SIZE, Y - HALF_SIZE, 1.0F, 1.0F, alpha);
        }
    }

    private static void putVertex(VertexConsumer consumer, Matrix4f model, float x, float y, float u, float v,
                                  float alpha) {
        consumer.vertex(model, x, y, Z)
                .color(alpha, alpha, alpha, alpha)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(FULL_BRIGHT)
                .normal(0.0F, 1.0F, 0.0F);
    }
}
