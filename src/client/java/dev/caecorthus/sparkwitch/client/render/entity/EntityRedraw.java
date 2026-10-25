package dev.caecorthus.sparkwitch.client.render.entity;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Entity render protocol, part 1 (2026-10-25): the only sanctioned way to draw an entity again outside the world's own
 * entity loop. It goes through {@link EntityRenderDispatcher#render}, where Entity Texture Features mounts the
 * per-entity render state that Entity Model Features animates with. Calling {@code EntityRenderer#render} directly
 * animates the shared model with no state or with the last-mounted entity's state, so an animation pack's pose comes out
 * wrong and changes every frame (the Blind's silhouettes twitched and doubled up, playtest 2026-10-25). Shadow, fire
 * and hitbox are skipped ({@code EntityRenderProtocolDispatcherMixin}); feature, label and look gates belong to the
 * caller, keyed on {@link #isActive()}. Culling mods that cancel {@code WorldRenderer#renderEntity} (EntityCulling) do
 * not stop it. Do not redraw an entity the world pass already drew this frame: frame-time animation would advance twice
 * and the two poses differ; reuse its {@link EntityModelCapture} geometry instead. Render thread only.
 * 实体渲染协议第 1 部分（2026-10-25）：在世界自身的实体循环之外再次绘制实体的唯一许可方式。它经由
 * {@link EntityRenderDispatcher#render}，Entity Texture Features 在此挂载每个实体的渲染状态，Entity Model Features 据此做动画。
 * 直接调用 {@code EntityRenderer#render} 会让共享模型在没有状态或沿用上一个挂载实体状态的情况下做动画，动画包的姿势因此出错且每帧
 * 变化（盲人轮廓抽搐、重影，2026-10-25 试玩）。阴影、火焰与碰撞箱会被跳过（{@code EntityRenderProtocolDispatcherMixin}）；
 * 附加层、名字标签与外观闸门由调用方根据 {@link #isActive()} 自行处理。取消 {@code WorldRenderer#renderEntity} 的剔除模组
 * （EntityCulling）不会拦住它。不要重绘世界 pass 本帧已画过的实体：按帧时长推进的动画会推进两次，两个姿势不一致；
 * 应改用它的 {@link EntityModelCapture} 几何。仅限渲染线程。
 */
public final class EntityRedraw {
    private static int depth;

    private EntityRedraw() {
    }

    /** True while {@link #draw} runs. / {@link #draw} 执行期间为真。 */
    public static boolean isActive() {
        return depth > 0;
    }

    /**
     * Draws {@code entity} at its interpolated position relative to {@code camera}, placed like
     * {@code WorldRenderer#renderEntity}, into {@code provider}. A renderer failure propagates as the dispatcher's
     * {@code CrashException}.
     * 以 {@code WorldRenderer#renderEntity} 的方式，把 {@code entity} 画在相对 {@code camera} 的插值位置，写入 {@code provider}。
     * 渲染器出错时以分发器的 {@code CrashException} 抛出。
     */
    public static void draw(EntityRenderDispatcher dispatcher, Entity entity, float tickDelta, Vec3d camera,
                            VertexConsumerProvider provider, int light) {
        double x = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - camera.x;
        double y = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - camera.y;
        double z = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - camera.z;
        float yaw = MathHelper.lerp(tickDelta, entity.prevYaw, entity.getYaw());
        depth++;
        try {
            dispatcher.render(entity, x, y, z, yaw, tickDelta, new MatrixStack(), provider, light);
        } finally {
            depth--;
        }
    }
}
