package dev.caecorthus.sparkwitch.client.bear.helmet;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.RysTHelmetItem;
import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.RysTVisor;
import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.RysTVisorGeometry;
import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.VisorCrack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;

/**
 * Bullet holes on the worn Rys-T glass, for every viewer (art README section 3): one flat quad per
 * {@link VisorCrack}, {@link #DECAL_UNITS} model units square, 0.05 in front of the glass face, centred at
 * (4 + 8u, 9 - 3v) and clipped to the glass (u = 0 at the wearer's left, v = 0 at the top, as on the HUD), using cell
 * {@code style} of the 16-px decal strip. Drawn right after vanilla {@code HeadFeatureRenderer} drew the helmet item, in
 * the same head matrix (so EMF head animation is followed) with the item model's own {@code display.head} transform;
 * on the visor-up model the quad follows the plate (+9.5 y, then 45 degrees about +x around (y 12.5, z 1)). The decal is
 * not rotated on the model (2.5 units leave no room; the HUD rotates). Presentation only.
 * 所有观察者都能看到的穿戴 Rys-T 玻璃弹孔（美术 README 第 3 节）：每个 {@link VisorCrack} 一个平面四边形，边长
 * {@link #DECAL_UNITS} 个模型单位，位于玻璃面前方 0.05，以 (4 + 8u, 9 - 3v) 为中心并裁剪到玻璃内（u = 0 为佩戴者左侧，v = 0 为
 * 顶部，与 HUD 相同），使用 16 像素贴花条中的第 {@code style} 格。在原版 {@code HeadFeatureRenderer} 绘制头盔物品之后立即绘制，
 * 使用同一头部矩阵（因此跟随 EMF 头部动画）与物品模型自身的 {@code display.head} 变换；面罩抬起模型上四边形跟随面板（先 +9.5 y，
 * 再绕 +x 以 (y 12.5, z 1) 为中心旋转 45 度）。贴花在模型上不旋转（2.5 单位没有余地；HUD 上会旋转）。仅为展示。
 */
public final class RysTWornCracks {
    public static final Identifier TEXTURE = SparkWitch.id("textures/entity/bear/visor_cracks_small.png");
    /** Decal edge in model units. / 贴花边长（模型单位）。 */
    public static final float DECAL_UNITS = 2.5F;
    /** Lift off the glass face, in model units. / 离开玻璃面的距离（模型单位）。 */
    public static final float LIFT_UNITS = 0.05F;
    /** Cells in the 16-px decal strip (128 x 16). / 16 像素贴花条的格数（128 × 16）。 */
    public static final int STRIP_CELLS = 8;
    private static final float UNIT = 1.0F / 16.0F;

    private RysTWornCracks() {
    }

    /** One clipped decal: model-unit rect and strip UVs. / 一个裁剪后的贴花：模型单位矩形与贴花条 UV。 */
    record Quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
    }

    /**
     * Pure placement of one crack on the visor-down glass, or null when nothing is left after clipping.
     * 一个裂纹在面罩放下玻璃上的纯放置计算；裁剪后无剩余时为 null。
     */
    static @Nullable Quad quad(VisorCrack crack) {
        float half = DECAL_UNITS / 2.0F;
        float cx = RysTVisorGeometry.GLASS_MIN_X + crack.u() * (RysTVisorGeometry.GLASS_MAX_X - RysTVisorGeometry.GLASS_MIN_X);
        float cy = RysTVisorGeometry.GLASS_MAX_Y - crack.v() * (RysTVisorGeometry.GLASS_MAX_Y - RysTVisorGeometry.GLASS_MIN_Y);
        float left = cx - half;
        float top = cy + half;
        float x0 = Math.max(left, RysTVisorGeometry.GLASS_MIN_X);
        float x1 = Math.min(cx + half, RysTVisorGeometry.GLASS_MAX_X);
        float y0 = Math.max(cy - half, RysTVisorGeometry.GLASS_MIN_Y);
        float y1 = Math.min(top, RysTVisorGeometry.GLASS_MAX_Y);
        if (!(x1 > x0) || !(y1 > y0)) {
            return null;
        }
        // Texture u runs with +x (the wearer's right, seen from inside), v runs down. / 贴图 u 随 +x，v 向下。
        float cell = 1.0F / STRIP_CELLS;
        float cellU = RysTVisorOverlayLayout.style(crack.style()) * cell;
        return new Quad(x0, y0, x1, y1,
                cellU + (x0 - left) / DECAL_UNITS * cell, (top - y1) / DECAL_UNITS,
                cellU + (x1 - left) / DECAL_UNITS * cell, (top - y0) / DECAL_UNITS);
    }

    /** Called right after the head feature drew {@code entity}'s head item. / 头部特征层绘制头部物品后立即调用。 */
    public static void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                              LivingEntity entity) {
        ItemStack helmet = RysTHelmetItem.wornHelmet(entity);
        if (helmet.isEmpty()) {
            return;
        }
        RysTVisor visor = RysTHelmetItem.visor(helmet);
        if (visor.cracks().isEmpty()) {
            return;
        }
        // The same model and seed HeldItemRenderer used, so the head transform is the one just drawn.
        // 与 HeldItemRenderer 相同的模型与种子，因此头部变换正是刚刚绘制所用的。
        BakedModel model = MinecraftClient.getInstance().getItemRenderer().getModel(helmet, entity.getWorld(), entity,
                entity.getId() + ModelTransformationMode.HEAD.ordinal());
        matrices.push();
        model.getTransformation().getTransformation(ModelTransformationMode.HEAD).apply(false, matrices);
        matrices.translate(-0.5F, -0.5F, -0.5F);
        if (!visor.down()) {
            matrices.translate(8.0F * UNIT, RysTVisorGeometry.UP_PIVOT_Y * UNIT, RysTVisorGeometry.UP_PIVOT_Z * UNIT);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(RysTVisorGeometry.UP_TILT_DEGREES));
            matrices.translate(-8.0F * UNIT, -RysTVisorGeometry.UP_PIVOT_Y * UNIT,
                    -RysTVisorGeometry.UP_PIVOT_Z * UNIT);
            matrices.translate(0.0F, RysTVisorGeometry.UP_LIFT_Y * UNIT, 0.0F);
        }
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
        MatrixStack.Entry entry = matrices.peek();
        float z = (RysTVisorGeometry.GLASS_FACE_Z - LIFT_UNITS) * UNIT;
        for (VisorCrack crack : visor.cracks()) {
            Quad quad = quad(crack);
            if (quad != null) {
                emit(consumer, entry, quad, z, light);
            }
        }
        matrices.pop();
    }

    private static void emit(VertexConsumer consumer, MatrixStack.Entry entry, Quad quad, float z, int light) {
        float x0 = quad.x0() * UNIT;
        float x1 = quad.x1() * UNIT;
        float y0 = quad.y0() * UNIT;
        float y1 = quad.y1() * UNIT;
        vertex(consumer, entry, x0, y1, z, quad.u0(), quad.v0(), light);
        vertex(consumer, entry, x0, y0, z, quad.u0(), quad.v1(), light);
        vertex(consumer, entry, x1, y0, z, quad.u1(), quad.v1(), light);
        vertex(consumer, entry, x1, y1, z, quad.u1(), quad.v0(), light);
    }

    private static void vertex(VertexConsumer consumer, MatrixStack.Entry entry, float x, float y, float z,
                               float u, float v, int light) {
        consumer.vertex(entry, x, y, z).color(255, 255, 255, 255).texture(u, v).overlay(OverlayTexture.DEFAULT_UV)
                .light(light).normal(entry, 0.0F, 0.0F, -1.0F);
    }
}
