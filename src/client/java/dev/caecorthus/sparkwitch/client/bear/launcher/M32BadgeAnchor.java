package dev.caecorthus.sparkwitch.client.bear.launcher;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

/**
 * Client only: where on the screen the M32A1 readout badge ({@link M32Hud}) is anchored this frame, captured from the
 * launcher the first-person hand pass actually drew (owner, 2026-11-06, option "B 跟随枪身": the badge rides the gun
 * through view bob, sway and the hip-to-aim blend instead of sitting above the hotbar slot).
 * <p>
 * Anchor contract. The two anchor points live here and nowhere else, in SIGHT MODEL SPACE (vertex space of
 * {@code item/m32a1_sight}, model units / 16; the sight model shares the full model's coordinates, so the hip anchor
 * is also a point of {@code item/m32a1}): {@link #HIP_ANCHOR_SIGHT_SPACE} and {@link #AIM_ANCHOR_SIGHT_SPACE}.
 * UPDATE BOTH WHEN THE SIGHT MODEL CHANGES; the projection and placement maths never name them.
 * {@link M32SightRenderer} projects both anchors through the launcher's one pose (camera-aligned at full aim) with
 * the hand pass's own clip matrix (hand projection x model-view x base, exactly what the reticle window test
 * uses), lerps the two screen points by the renderer's ease(p) so nothing pops, and stores the result with the current
 * frame id. At p = 0 vanilla draws the full model at the hip, so both poses are the hip pose and the blend is 0.
 * <p>
 * Validity. {@link #beginFrame} advances the frame id at {@code WorldRenderEvents.START}; the hand pass runs after
 * every world pass of the frame and the HUD after the hand pass, so {@link #current} answers only an anchor captured
 * in this very frame and never a stale one: no first-person hand pass (F5, hand hidden, the launcher not drawn, a
 * point behind the camera) means null and the HUD falls back to the slot badge. Render thread only; presentation
 * only, the server decides every shot and never sees any of this.
 * 仅客户端：本帧 M32A1 读数标记（{@link M32Hud}）在屏幕上的锚点，取自第一人称手部渲染实际绘制的发射器（所有者 2026-11-06，
 * 方案 "B 跟随枪身"：标记随枪身经历视角摇晃、摆动与腰射到瞄准的过渡，而不是固定在快捷栏格上方）。
 * <p>
 * 锚点契约。两个锚点只在这里定义，使用瞄具模型空间（{@code item/m32a1_sight} 的顶点空间，模型单位 / 16；瞄具模型与完整模型共用
 * 坐标，因此腰射锚点同时也是 {@code item/m32a1} 中的点）：{@link #HIP_ANCHOR_SIGHT_SPACE} 与 {@link #AIM_ANCHOR_SIGHT_SPACE}。
 * 瞄具模型改动时请一并更新这两个常量；投影与布局数学从不引用它们。{@link M32SightRenderer} 用本次手部渲染自己的裁剪矩阵（手部
 * 投影 x 模型视图 x 基准，与分划窗口检测所用完全相同）把两个锚点都经发射器的同一个姿态（完全瞄准时与相机对齐）投影到屏幕，再按渲染器的
 * ease(p) 对两个屏幕点做线性插值使其不跳变，并连同当前帧号一起保存。p = 0 时原版在腰射姿态绘制完整模型，因此两个姿态都是腰射
 * 姿态、混合为 0。
 * <p>
 * 有效性。{@link #beginFrame} 在 {@code WorldRenderEvents.START} 推进帧号；手部渲染在本帧全部世界渲染之后、HUD 在手部渲染之后，
 * 因此 {@link #current} 只返回本帧捕获的锚点，绝不返回过期值：没有第一人称手部渲染（F5、隐藏手部、未绘制发射器、点位于相机后方）
 * 即为 null，HUD 回退到格标记。仅渲染线程；仅为表现，每次发射都由服务端决定，服务端看不到这里的任何内容。
 */
public final class M32BadgeAnchor {
    /**
     * Hip anchor, sight model space (model units / 16): the top centre of the M2A1's top rail, (8, 24, 0) on the
     * v7 art (shroud top under the rail at y 23.5, sight axis (8, 21.5)). Update when the sight model changes.
     * 腰射锚点（瞄具模型空间，模型单位 / 16）：M2A1 顶部导轨的顶面中心，v7 美术为 (8, 24, 0)（导轨下的护套顶为 y 23.5，瞄具轴线为 (8, 21.5)）。瞄具模型改动时更新。
     */
    public static final Vector3fc HIP_ANCHOR_SIGHT_SPACE = new Vector3f(8.0F, 24.0F, 0.0F)
            .div(M32SightPose.UNITS_PER_BLOCK);
    /**
     * Aim anchor, sight model space (model units / 16): with the v7 art the sight's outermost drawn silhouette on the
     * sight-axis row is the right ear of the three-eared rear plate. The anchor is that ear's right outer face at the
     * plate's z middle, (11, 21.5, 3.25): the ring lands on the ear's edge and the badge sits beside the sight,
     * never over the bore. Update when the sight model changes.
     * 瞄准锚点（瞄具模型空间，模型单位 / 16）：v7 美术下，瞄具在轴线那一行最外侧的轮廓是三耳后挡板的右耳。锚点取该耳右外侧面在
     * 挡板 z 中点处的点 (11, 21.5, 3.25)：圆环落在耳缘上，标记位于瞄具旁，绝不压在内孔上。瞄具模型改动时更新。
     */
    public static final Vector3fc AIM_ANCHOR_SIGHT_SPACE = new Vector3f(11.0F, 21.5F, 3.25F)
            .div(M32SightPose.UNITS_PER_BLOCK);
    private static final float MIN_W = 1.0E-4F;
    private static long frame;
    private static long capturedFrame = -1L;
    private static float x;
    private static float y;
    private static float aimBlend;

    private M32BadgeAnchor() {
    }

    /** {@code WorldRenderEvents.START}: a new frame; any earlier capture is stale. / 新的一帧，先前的捕获已过期。 */
    public static void beginFrame() {
        frame++;
    }

    /**
     * Hand pass: records the blended screen point of the two anchors for this frame, or clears it when either point
     * does not project ({@link #screenPoint}). {@code blend} is the renderer's ease(p).
     * 手部渲染：记录本帧两个锚点混合后的屏幕点；任一点无法投影时清除（{@link #screenPoint}）。{@code blend} 为渲染器的 ease(p)。
     */
    public static void capture(Matrix4fc clipFromBase, Matrix4fc hipPose, Matrix4fc sightPose, float blend,
                               int scaledWidth, int scaledHeight) {
        float[] point = screenPoint(clipFromBase, hipPose, HIP_ANCHOR_SIGHT_SPACE, sightPose, AIM_ANCHOR_SIGHT_SPACE,
                blend, scaledWidth, scaledHeight);
        if (point == null) {
            capturedFrame = -1L;
            return;
        }
        x = point[0];
        y = point[1];
        aimBlend = clamp01(blend);
        capturedFrame = frame;
    }

    /** The anchor captured in this frame, or null (fall back to the slot badge). / 本帧捕获的锚点；无则为 null。 */
    @Nullable
    public static Anchor current() {
        return capturedFrame == frame ? new Anchor(x, y, aimBlend) : null;
    }

    /**
     * Pure, independent of the anchor constants: lerp({@code hipPoint} through {@code hipPose}, {@code aimPoint}
     * through {@code sightPose}, clamp01(blend)) in GUI px, or null when either point does not project (behind the
     * camera or non-finite). Both must project whatever the blend, so the answer never jumps when the blend leaves 0
     * or 1.
     * 纯函数，与锚点常量无关：lerp({@code hipPoint} 经 {@code hipPose}, {@code aimPoint} 经 {@code sightPose}, clamp01(blend))，
     * GUI 像素；任一点无法投影（位于相机后方或非有限）时为 null。无论混合值如何两点都必须可投影，因此混合离开 0 或 1 时结果不会
     * 跳变。
     */
    public static float @Nullable [] screenPoint(Matrix4fc clipFromBase, Matrix4fc hipPose, Vector3fc hipPoint,
                                                 Matrix4fc sightPose, Vector3fc aimPoint, float blend, int scaledWidth,
                                                 int scaledHeight) {
        float[] hip = project(clipFromBase, hipPose, hipPoint, scaledWidth, scaledHeight);
        float[] aim = project(clipFromBase, sightPose, aimPoint, scaledWidth, scaledHeight);
        if (hip == null || aim == null) {
            return null;
        }
        float t = clamp01(blend);
        return new float[]{hip[0] + (aim[0] - hip[0]) * t, hip[1] + (aim[1] - hip[1]) * t};
    }

    /**
     * Pure: {@code point} (vertex space) through {@code pose} then {@code clipFromBase} to GUI px: x = (ndc.x + 1) / 2
     * x width, y = (1 - ndc.y) / 2 x height. Null unless the clip w is positive and the result finite.
     * 纯函数：{@code point}（顶点空间）经 {@code pose} 再经 {@code clipFromBase} 到 GUI 像素：x = (ndc.x + 1) / 2 x 宽，
     * y = (1 - ndc.y) / 2 x 高。裁剪 w 不为正或结果非有限时为 null。
     */
    public static float @Nullable [] project(Matrix4fc clipFromBase, Matrix4fc pose, Vector3fc point, int scaledWidth,
                                             int scaledHeight) {
        Vector4f clip = pose.transform(new Vector4f(point.x(), point.y(), point.z(), 1.0F));
        clipFromBase.transform(clip);
        if (!(clip.w > MIN_W)) {
            return null;
        }
        float px = (clip.x / clip.w + 1.0F) * 0.5F * scaledWidth;
        float py = (1.0F - clip.y / clip.w) * 0.5F * scaledHeight;
        return Float.isFinite(px) && Float.isFinite(py) ? new float[]{px, py} : null;
    }

    private static float clamp01(float value) {
        return Float.isNaN(value) ? 0.0F : Math.max(0.0F, Math.min(1.0F, value));
    }

    /**
     * One frame's anchor: GUI px and the aim blend (0 hip, 1 full aim) the placement follows.
     * 单帧锚点：GUI 像素坐标与布局所跟随的瞄准混合值（0 腰射、1 完全瞄准）。
     */
    public record Anchor(float x, float y, float aimBlend) {
    }
}
