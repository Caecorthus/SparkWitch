package dev.caecorthus.sparkwitch.client.render.entity;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumers;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Entity render protocol, part 2 (2026-10-25): lends a secondary effect the exact body geometry the world's own entity
 * pass drew this frame. A {@link Recorder} opened before the world render ({@code WorldRenderEvents.START}) receives,
 * through {@link #tee}, every vertex of the main body model that {@code LivingEntityRenderer#render} draws inside the
 * {@code EntityRenderDispatcher#render} of an entity it {@link Recorder#wants wants}, in camera-relative world space
 * (the world pass's own matrices). Whatever posed that model (vanilla, Entity Model Features with an animation pack,
 * another animation mod, a replaced body drawn under that entity) is matched exactly, and nothing is animated twice.
 * Feature layers, shadows and labels are not recorded, nor is anything inside an {@link EntityRedraw} or a
 * {@link SecondaryWorldPass}. An entity the world pass did not draw (frustum, Sodium or EntityCulling culling) is never
 * reported as recorded; draw it with {@link EntityRedraw}. One recorder at a time; render thread only.
 * 实体渲染协议第 2 部分（2026-10-25）：把世界自身的实体 pass 本帧画出的精确身体几何借给次级效果。在世界渲染之前
 * （{@code WorldRenderEvents.START}）打开的 {@link Recorder}，会经由 {@link #tee} 收到它 {@link Recorder#wants 想要} 的实体在
 * {@code EntityRenderDispatcher#render} 内由 {@code LivingEntityRenderer#render} 绘制的主身体模型的每个顶点，坐标为以镜头为
 * 原点的世界空间（世界 pass 自己的矩阵）。无论模型由什么摆出姿势（原版、Entity Model Features 加动画包、其他动画模组、
 * 在该实体之下绘制的替换身体），都能精确吻合，且不会做第二次动画。附加层、阴影与名字标签不录制，{@link EntityRedraw} 与
 * {@link SecondaryWorldPass} 内的任何内容也不录制。世界 pass 未绘制的实体（视锥、Sodium 或 EntityCulling 剔除）不会被报告为
 * 已录制；请用 {@link EntityRedraw} 绘制它。同一时间只有一个录制器；仅限渲染线程。
 */
public final class EntityModelCapture {
    @Nullable
    private static Recorder recorder;
    /** Outermost entity of the running {@code EntityRenderDispatcher#render}. / 正在执行的最外层分发渲染的实体。 */
    @Nullable
    private static Entity dispatched;
    private static int dispatchDepth;

    private EntityModelCapture() {
    }

    /**
     * A per-frame consumer of recorded body geometry.
     * 每帧录制身体几何的接收方。
     */
    public interface Recorder {
        /** Whether to record {@code entity}'s body this frame. / 本帧是否录制 {@code entity} 的身体。 */
        boolean wants(Entity entity);

        /**
         * Receives the recorded vertices; it must drop writes made after the recorder's frame ends.
         * 接收录制的顶点；在录制器所属帧结束后的写入必须丢弃。
         */
        VertexConsumer sink();

        /** {@code entity}'s body model is being recorded. / 正在录制 {@code entity} 的身体模型。 */
        void recorded(Entity entity);
    }

    /** Opens {@code next}, replacing any open recorder. / 打开 {@code next}，替换已打开的录制器。 */
    public static void open(Recorder next) {
        recorder = Objects.requireNonNull(next, "recorder");
    }

    /** Closes {@code owner} if it is the open recorder. / 若 {@code owner} 是当前录制器则关闭它。 */
    public static void close(Recorder owner) {
        if (recorder == owner) {
            recorder = null;
        }
    }

    /** {@code EntityRenderProtocolDispatcherMixin}: entering a dispatch. / 进入一次分发渲染。 */
    public static void enterDispatch(Entity entity) {
        if (dispatchDepth++ == 0) {
            dispatched = entity;
        }
    }

    /** {@code EntityRenderProtocolDispatcherMixin}: leaving a dispatch. / 离开一次分发渲染。 */
    public static void exitDispatch() {
        if (--dispatchDepth <= 0) {
            dispatchDepth = 0;
            dispatched = null;
        }
    }

    /**
     * {@code EntityModelCaptureMixin}: the consumer the body model draws into; {@code vertices} itself unless the open
     * recorder wants the dispatched entity, in which case the vertices also go to its sink (vanilla
     * {@link VertexConsumers#union}, so renderer mods keep their usual handling of the original consumer).
     * {@code EntityModelCaptureMixin}：身体模型写入的消费者；除非当前录制器想要正在分发的实体，否则就是 {@code vertices} 本身；
     * 想要时顶点同时写入其接收方（原版 {@link VertexConsumers#union}，渲染类模组对原消费者的处理照旧）。
     */
    public static VertexConsumer tee(VertexConsumer vertices) {
        Recorder active = recorder;
        Entity entity = dispatched;
        if (active == null || entity == null
                || !records(EntityRedraw.isActive(), SecondaryWorldPass.isActive())
                || !active.wants(entity)) {
            return vertices;
        }
        active.recorded(entity);
        return VertexConsumers.union(vertices, active.sink());
    }

    /**
     * Pure rule: never record a redraw or a secondary world pass.
     * 纯规则：从不录制重绘与第二次世界渲染。
     */
    static boolean records(boolean redrawing, boolean secondaryWorldPass) {
        return !redrawing && !secondaryWorldPass;
    }
}
