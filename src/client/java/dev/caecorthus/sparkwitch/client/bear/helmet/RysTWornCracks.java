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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Bullet holes on the worn Rys-T glass, for every viewer: one flat quad per {@link VisorCrack}, {@link #DECAL_UNITS}
 * model units square, {@link #LIFT_UNITS} in front of the glass face (the crack plane z 1.70), centred at
 * (3 + 10u, 9 - 3v) and then shifted so the whole square stays on the 10 x 3 glass window (u = 0 at the wearer's left,
 * v = 0 at the top, as on the HUD), using cell {@code style} of the decal strip. Drawn right after vanilla
 * {@code HeadFeatureRenderer} drew the helmet item, in the same head matrix (so EMF head animation is followed) with the
 * item model's own {@code display.head} transform; on the visor-up model the quad follows the plate through the
 * {@link RysTVisorGeometry} raised pose ({@link #applyRaisedPose}). The decal is not rotated on the model: a rotated
 * 2.5-unit square would overhang the 3-unit-tall window (the HUD rotates). Presentation only.
 * 所有观察者都能看到的穿戴 Rys-T 玻璃弹孔：每个 {@link VisorCrack} 一个平面四边形，边长 {@link #DECAL_UNITS} 个模型单位，
 * 位于玻璃面前方 {@link #LIFT_UNITS}（裂纹平面 z 1.70），以 (3 + 10u, 9 - 3v) 为中心，再平移使整个正方形留在 10 × 3 的玻璃窗内
 * （u = 0 为佩戴者左侧，v = 0 为顶部，与 HUD 相同），使用贴花条中的第 {@code style} 格。在原版
 * {@code HeadFeatureRenderer} 绘制头盔物品之后立即绘制，使用同一头部矩阵（因此跟随 EMF 头部动画）与物品模型自身的
 * {@code display.head} 变换；面罩抬起模型上四边形通过 {@link RysTVisorGeometry} 的抬起姿态（{@link #applyRaisedPose}）跟随面板。
 * 贴花在模型上不旋转：旋转后的 2.5 单位正方形会超出 3 单位高的玻璃窗（HUD 上会旋转）。仅为展示。
 */
public final class RysTWornCracks {
    public static final Identifier TEXTURE = SparkWitch.id("textures/entity/bear/visor_cracks_small.png");
    /** Decal edge in model units (fits the 3-unit-tall window). / 贴花边长（模型单位，可放入 3 单位高的玻璃窗）。 */
    public static final float DECAL_UNITS = 2.5F;
    /** Lift off the glass face, in model units. / 离开玻璃面的距离（模型单位）。 */
    public static final float LIFT_UNITS = 0.05F;
    /** Cells in the decal strip (256 x 32, 32-px cells). / 贴花条的格数（256 × 32，每格 32 像素）。 */
    public static final int STRIP_CELLS = 8;
    private static final float UNIT = 1.0F / 16.0F;

    private RysTWornCracks() {
    }

    /** One decal: model-unit rect on the visor-down glass and strip UVs. / 一个贴花：放下玻璃上的模型单位矩形与贴花条 UV。 */
    record Quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
    }

    /**
     * Pure placement of one crack on the visor-down glass: the whole decal, kept inside the glass.
     * 一个裂纹在面罩放下玻璃上的纯放置计算：完整贴花，保持在玻璃内。
     */
    static Quad quad(VisorCrack crack) {
        float half = DECAL_UNITS / 2.0F;
        float cx = inside(RysTVisorGeometry.GLASS_MIN_X
                        + crack.u() * (RysTVisorGeometry.GLASS_MAX_X - RysTVisorGeometry.GLASS_MIN_X),
                RysTVisorGeometry.GLASS_MIN_X, RysTVisorGeometry.GLASS_MAX_X, half);
        float cy = inside(RysTVisorGeometry.GLASS_MAX_Y
                        - crack.v() * (RysTVisorGeometry.GLASS_MAX_Y - RysTVisorGeometry.GLASS_MIN_Y),
                RysTVisorGeometry.GLASS_MIN_Y, RysTVisorGeometry.GLASS_MAX_Y, half);
        // Texture u runs with +x (the wearer's right, seen from inside), v runs down. / 贴图 u 随 +x，v 向下。
        float cell = 1.0F / STRIP_CELLS;
        float cellU = RysTVisorOverlayLayout.style(crack.style()) * cell;
        return new Quad(cx - half, cy - half, cx + half, cy + half, cellU, 0.0F, cellU + cell, 1.0F);
    }

    private static float inside(float centre, float min, float max, float half) {
        return max - min <= 2.0F * half ? (min + max) / 2.0F : MathHelper.clamp(centre, min + half, max - half);
    }

    /**
     * Moves model-unit geometry from the visor-down pose onto the raised visor:
     * {@code up = P + Rx(UP_TILT_DEGREES) * (down - P)} with no lift ({@link RysTVisorGeometry}); with the art v8
     * quarter turn the crack plane z 1.70 lands flat at y 14.30, just above the raised glass, facing up.
     * 把模型单位几何从面罩放下姿态移到抬起的面罩上：{@code up = P + Rx(UP_TILT_DEGREES) * (down - P)}，不上移
     * （{@link RysTVisorGeometry}）；美术 v8 转四分之一圈后，裂纹平面 z 1.70 平躺在 y 14.30，紧贴抬起的玻璃上方并朝上。
     */
    static void applyRaisedPose(MatrixStack matrices) {
        matrices.translate(RysTVisorGeometry.UP_PIVOT_X, RysTVisorGeometry.UP_PIVOT_Y, RysTVisorGeometry.UP_PIVOT_Z);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(RysTVisorGeometry.UP_TILT_DEGREES));
        matrices.translate(-RysTVisorGeometry.UP_PIVOT_X, -RysTVisorGeometry.UP_PIVOT_Y,
                -RysTVisorGeometry.UP_PIVOT_Z);
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
        // Model units from here, as in the item JSON. / 此后使用模型单位，与物品 JSON 一致。
        matrices.scale(UNIT, UNIT, UNIT);
        if (!visor.down()) {
            applyRaisedPose(matrices);
        }
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
        MatrixStack.Entry entry = matrices.peek();
        float z = RysTVisorGeometry.GLASS_FACE_Z - LIFT_UNITS;
        for (VisorCrack crack : visor.cracks()) {
            emit(consumer, entry, quad(crack), z, light);
        }
        matrices.pop();
    }

    private static void emit(VertexConsumer consumer, MatrixStack.Entry entry, Quad quad, float z, int light) {
        vertex(consumer, entry, quad.x0(), quad.y1(), z, quad.u0(), quad.v0(), light);
        vertex(consumer, entry, quad.x0(), quad.y0(), z, quad.u0(), quad.v1(), light);
        vertex(consumer, entry, quad.x1(), quad.y0(), z, quad.u1(), quad.v1(), light);
        vertex(consumer, entry, quad.x1(), quad.y1(), z, quad.u1(), quad.v0(), light);
    }

    private static void vertex(VertexConsumer consumer, MatrixStack.Entry entry, float x, float y, float z,
                               float u, float v, int light) {
        consumer.vertex(entry, x, y, z).color(255, 255, 255, 255).texture(u, v).overlay(OverlayTexture.DEFAULT_UV)
                .light(light).normal(entry, 0.0F, 0.0F, -1.0F);
    }
}
