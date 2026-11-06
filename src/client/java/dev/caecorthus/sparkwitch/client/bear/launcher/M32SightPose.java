package dev.caecorthus.sparkwitch.client.bear.launcher;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

/**
 * Pure maths of the M32A1's 3-D M2A1 red-dot aim (SIGHT-CONTRACT v2, 2026-11-06): the aim progress and its ease, the
 * hand FOV, the range tilt, the full-aim pose, the hip-to-aim blend, the draw correction and the dot's window test.
 * No Minecraft types, so every number is unit-tested.
 * <p>
 * Spaces. Model units: 1 = one texel of the 64x64 sheet, -z muzzle, +x the launcher's right, +y up (the art JSON).
 * Vertex space: model units / 16 (what a baked quad holds; {@code ItemRenderer} translates it by -0.5 after the
 * display transform). Base space: the hand-pass matrix captured at HEAD of the first-person
 * {@code HeldItemRenderer#renderItem(float, ...)} ({@code B}); it already carries hurt tilt and view bob, and the
 * pass's model-view undoes its camera rotation, so base space is camera space (+x right, +y up, -z forward). Every
 * pose here maps vertex space to base space.
 * <p>
 * The full aim pose {@link #aimPose} keeps the camera's orientation, the first-person scale {@link #AIM_SCALE} and
 * puts the TILTED eye point {@code P + Rx(-tilt)(E - P)} at the origin. The sight model is that pose times
 * {@link #sightTilt} (nose down about +x through the pivot). {@link #blend} moves the hip pose to it: the anchor's image
 * is lerped, the rotation slerped and the uniform scale lerped.
 * M32A1 三维 M2A1 红点瞄准的纯数学部分（SIGHT-CONTRACT v2，2026-11-06）：瞄准进度与缓动、手部视场角、射程俯仰、完全瞄准
 * 姿态、腰射到瞄准的过渡、绘制校正以及红点的窗口检测。不含 Minecraft 类型，因此每个数值都有单元测试。
 * <p>
 * 坐标空间。模型单位：1 = 64x64 贴图的一个像素，-z 为炮口，+x 为发射器右侧，+y 向上（美术 JSON）。顶点空间：模型单位 / 16
 * （烘焙四边形中的坐标；{@code ItemRenderer} 在显示变换之后再平移 -0.5）。基准空间：在第一人称
 * {@code HeldItemRenderer#renderItem(float, ...)} 的 HEAD 捕获的手部渲染矩阵（{@code B}）；它已包含受伤倾斜与视角摇晃，且该渲染
 * 的模型视图矩阵抵消了其中的相机旋转，因此基准空间即相机空间（+x 右、+y 上、-z 前）。这里的每个姿态都把顶点空间映射到基准空间。
 * <p>
 * 完全瞄准姿态 {@link #aimPose} 保持相机朝向与第一人称缩放 {@link #AIM_SCALE}，并把俯仰后的眼点
 * {@code P + Rx(-tilt)(E - P)} 放在原点。瞄具模型为该姿态乘以 {@link #sightTilt}（绕经过枢轴的 +x 轴低头）。
 * {@link #blend} 把腰射姿态移向它：锚点的像做线性插值，旋转做球面插值，均匀缩放做线性插值。
 */
public final class M32SightPose {
    /** Model units per block (vertex space = model / 16). / 每格的模型单位数。 */
    public static final float UNITS_PER_BLOCK = 16.0F;
    /** Eye point behind the open window, model units. / 开放窗口后方的眼点（模型单位）。 */
    public static final Vector3fc EYE = new Vector3f(7.5F, 20.25F, 19.0F);
    /** Range-tilt pivot (the mount), model units. / 射程俯仰枢轴（瞄具座），模型单位。 */
    public static final Vector3fc PIVOT = new Vector3f(7.5F, 18.5F, 0.5F);
    /** The open window tunnel, model units. / 开放窗口通道（模型单位）。 */
    public static final float WINDOW_X0 = 6.0F;
    public static final float WINDOW_X1 = 9.0F;
    public static final float WINDOW_Y0 = 19.0F;
    public static final float WINDOW_Y1 = 21.5F;
    public static final float WINDOW_FRONT_Z = -1.0F;
    public static final float WINDOW_REAR_Z = 2.0F;
    /** The dot's plane on the sight axis (70 units in front of the eye). / 红点所在平面（眼点前方 70 单位）。 */
    public static final float DOT_Z = -51.0F;
    /**
     * Halo and core quad edges, model units (about 0.65 and 0.18 degrees from the eye; the halo's solid part is about
     * half of it). / 光晕与核心四边形边长（模型单位，自眼点看约 0.65 与 0.18 度；光晕实心部分约为其一半）。
     */
    public static final float HALO_EDGE = 0.8F;
    public static final float CORE_EDGE = 0.22F;
    /**
     * The core sits this much nearer the eye than the halo, so the translucent layer's back-to-front sort draws it last.
     * 核心比光晕离眼睛近这么多，使半透明渲染层的由远及近排序最后绘制它。
     */
    public static final float CORE_LEAD = 0.05F;
    /** First-person scale of the full aim pose (the display block's 0.5). / 完全瞄准姿态的第一人称缩放。 */
    public static final float AIM_SCALE = 0.5F;
    /** Hand render FOV at full aim. / 完全瞄准时的手部渲染视场角。 */
    public static final double AIM_HAND_FOV_DEGREES = 45.0;
    /** World zoom while aiming (1.25x) and its look scale. / 瞄准时的世界放大（1.25 倍）及视角缩放。 */
    public static final float WORLD_FOV_MULTIPLIER = 0.8F;
    public static final float LOOK_MULTIPLIER = 0.8F;
    public static final double AIM_IN_SECONDS = 0.20;
    public static final double AIM_OUT_SECONDS = 0.15;
    /** Fail-safe bound on the range tilt; the dial never needs more. / 射程俯仰的失败安全上限，刻度从不需要更多。 */
    public static final double MAX_TILT_RADIANS = Math.toRadians(30.0);
    private static final float MIN_W = 1.0E-4F;

    private M32SightPose() {
    }

    /**
     * Aim progress after {@code seconds} of frame time: up to 1 over {@link #AIM_IN_SECONDS} while aiming, down to 0 over
     * {@link #AIM_OUT_SECONDS} after. Unusable time leaves it (clamped) where it was.
     * 经过 {@code seconds} 帧时间后的瞄准进度：瞄准时在 {@link #AIM_IN_SECONDS} 内升到 1，之后在 {@link #AIM_OUT_SECONDS} 内降到 0。
     * 不可用的时间保持原值（钳制后）。
     */
    public static float step(float progress, boolean aiming, double seconds) {
        float p = clamp01(progress);
        if (!(seconds > 0.0) || !Double.isFinite(seconds)) {
            return p;
        }
        return aiming ? (float) Math.min(1.0, p + seconds / AIM_IN_SECONDS)
                : (float) Math.max(0.0, p - seconds / AIM_OUT_SECONDS);
    }

    /** Smoothstep of the clamped progress. / 钳制后进度的 smoothstep。 */
    public static float ease(float progress) {
        float p = clamp01(progress);
        return p * p * (3.0F - 2.0F * p);
    }

    /**
     * Hand render FOV: lerp(vanilla, {@link #AIM_HAND_FOV_DEGREES}, ease(p)); exactly {@code vanillaFov} at 0.
     * 手部渲染视场角：lerp(原版, {@link #AIM_HAND_FOV_DEGREES}, ease(p))；为 0 时严格等于 {@code vanillaFov}。
     */
    public static double handFov(double vanillaFov, float progress) {
        float e = ease(progress);
        if (e <= 0.0F || !Double.isFinite(vanillaFov)) {
            return vanillaFov;
        }
        return vanillaFov + (AIM_HAND_FOV_DEGREES - vanillaFov) * e;
    }

    /**
     * The sight tilt h' that shows holdover {@code h} (world projection) at the same screen row through the hand
     * projection: atan(tan(h) * tan(handFov / 2) / tan(worldFov / 2)). NaN for unusable inputs or past
     * {@link #MAX_TILT_RADIANS}.
     * 使世界投影中的抬高量 {@code h} 经手部投影落在同一屏幕行的瞄具俯仰 h'：atan(tan(h) * tan(手部视场角 / 2) /
     * tan(世界视场角 / 2))。输入不可用或超过 {@link #MAX_TILT_RADIANS} 时为 NaN。
     */
    public static double tiltRadians(double holdoverRadians, double handFovDegrees, double worldFovDegrees) {
        if (!Double.isFinite(holdoverRadians) || !(Math.abs(holdoverRadians) < Math.PI / 2.0)
                || !usableFov(handFovDegrees) || !usableFov(worldFovDegrees)) {
            return Double.NaN;
        }
        double tilt = Math.atan(Math.tan(holdoverRadians) * Math.tan(Math.toRadians(handFovDegrees) / 2.0)
                / Math.tan(Math.toRadians(worldFovDegrees) / 2.0));
        return Math.abs(tilt) <= MAX_TILT_RADIANS ? tilt : Double.NaN;
    }

    /** P + Rx(-tilt)(E - P), model units. / 俯仰后的眼点（模型单位）。 */
    public static Vector3f tiltedEye(float tilt) {
        return sightTiltModel(tilt).transformPosition(new Vector3f(EYE));
    }

    /**
     * The sight's range tilt in vertex space: nose down by {@code tilt} radians about +x through the pivot.
     * 顶点空间中的瞄具射程俯仰：绕经过枢轴的 +x 轴低头 {@code tilt} 弧度。
     */
    public static Matrix4f sightTilt(float tilt) {
        Vector3f pivot = new Vector3f(PIVOT).div(UNITS_PER_BLOCK);
        return new Matrix4f().translation(pivot).rotateX(-tilt).translate(-pivot.x, -pivot.y, -pivot.z);
    }

    /**
     * Full aim pose of the body, vertex space to base space: camera orientation, scale {@link #AIM_SCALE}, the tilted
     * eye at the origin. / 机身的完全瞄准姿态（顶点空间到基准空间）：相机朝向、缩放 {@link #AIM_SCALE}、俯仰后的眼点位于原点。
     */
    public static Matrix4f aimPose(float tilt) {
        Vector3f eye = tiltedEye(tilt).div(UNITS_PER_BLOCK);
        return new Matrix4f().scaling(AIM_SCALE).translate(-eye.x, -eye.y, -eye.z);
    }

    /** The tilted eye in vertex space, the blend's anchor. / 顶点空间中的俯仰后眼点，即过渡锚点。 */
    public static Vector3f anchor(float tilt) {
        return tiltedEye(tilt).div(UNITS_PER_BLOCK);
    }

    /**
     * The hip pose relative to the base: {@code hipPre} (base to the stack at vanilla's item draw: lag sway, equip and
     * swing offsets) times the display transform and vanilla's -0.5 shift.
     * 相对基准的腰射姿态：{@code hipPre}（从基准到原版物品绘制时的矩阵栈：延迟摆动、装备与挥动偏移）乘以显示变换与原版的 -0.5 平移。
     */
    public static Matrix4f hipPose(Matrix4fc hipPre, Matrix4fc display) {
        return new Matrix4f(hipPre).mul(display).translate(-0.5F, -0.5F, -0.5F);
    }

    /**
     * Blends {@code hip} to {@code aim} by {@code t} in [0, 1]: the image of {@code anchor} is lerped, the rotation
     * slerped and the uniform scale lerped; t = 1 is {@code aim} and t = 0 is {@code hip} for any similarity transform.
     * Null when either pose is not an orientation-preserving similarity (non-finite or determinant not positive).
     * 按 {@code t}（[0, 1]）把 {@code hip} 过渡到 {@code aim}：{@code anchor} 的像线性插值，旋转球面插值，均匀缩放线性插值；
     * t = 1 即 {@code aim}，对任意相似变换 t = 0 即 {@code hip}。任一姿态不是保持定向的相似变换（非有限或行列式不为正）时为 null。
     */
    public static @Nullable Matrix4f blend(Matrix4fc hip, Matrix4fc aim, Vector3fc anchor, float t) {
        float s = clamp01(t);
        float hipScale = uniformScale(hip);
        float aimScale = uniformScale(aim);
        if (!(hipScale > 0.0F) || !(aimScale > 0.0F)) {
            return null;
        }
        Quaternionf hipRotation = hip.getUnnormalizedRotation(new Quaternionf());
        Quaternionf aimRotation = aim.getUnnormalizedRotation(new Quaternionf());
        Quaternionf rotation = hipRotation.slerp(aimRotation, s, new Quaternionf()).normalize();
        Vector3f hipAnchor = hip.transformPosition(new Vector3f(anchor));
        Vector3f aimAnchor = aim.transformPosition(new Vector3f(anchor));
        Vector3f center = hipAnchor.lerp(aimAnchor, s, new Vector3f());
        float scale = hipScale + (aimScale - hipScale) * s;
        Matrix4f blended = new Matrix4f().translation(center).rotate(rotation).scale(scale)
                .translate(-anchor.x(), -anchor.y(), -anchor.z());
        return blended.isFinite() ? blended : null;
    }

    /**
     * The matrix to multiply onto the stack at vanilla's item draw so that {@code ItemRenderer#renderItem}, which then
     * applies {@code display} and the -0.5 shift, ends at {@code target} (relative to the base):
     * {@code hipPre^-1 * target * T(0.5) * display^-1}. Null when a factor cannot be inverted.
     * 在原版物品绘制处乘到矩阵栈上的矩阵，使随后应用 {@code display} 与 -0.5 平移的 {@code ItemRenderer#renderItem} 最终落在
     * {@code target}（相对基准）：{@code hipPre^-1 * target * T(0.5) * display^-1}。任一因子不可逆时为 null。
     */
    public static @Nullable Matrix4f drawCorrection(Matrix4fc hipPre, Matrix4fc target, Matrix4fc display) {
        Matrix4f inverseHipPre = invert(hipPre);
        Matrix4f inverseDisplay = invert(display);
        if (inverseHipPre == null || inverseDisplay == null) {
            return null;
        }
        Matrix4f correction = inverseHipPre.mul(target).translate(0.5F, 0.5F, 0.5F).mul(inverseDisplay);
        return correction.isFinite() ? correction : null;
    }

    /** {@code hipPre^-1 * target} (a quad drawn straight in vertex space), or null. / 直接在顶点空间绘制时的矩阵。 */
    public static @Nullable Matrix4f directCorrection(Matrix4fc hipPre, Matrix4fc target) {
        Matrix4f inverseHipPre = invert(hipPre);
        if (inverseHipPre == null) {
            return null;
        }
        Matrix4f correction = inverseHipPre.mul(target);
        return correction.isFinite() ? correction : null;
    }

    /** The inverse, or null when singular or non-finite. / 逆矩阵；奇异或非有限时为 null。 */
    public static @Nullable Matrix4f invert(Matrix4fc matrix) {
        float det = matrix.determinant();
        if (!Float.isFinite(det) || Math.abs(det) < 1.0E-12F) {
            return null;
        }
        Matrix4f inverse = matrix.invert(new Matrix4f());
        return inverse.isFinite() ? inverse : null;
    }

    /** The dot's centre on the sight axis, vertex space. / 红点中心（瞄具轴上，顶点空间）。 */
    public static Vector3f dotCenter() {
        return new Vector3f(EYE.x(), EYE.y(), DOT_Z).div(UNITS_PER_BLOCK);
    }

    /**
     * The four front-window corners in vertex space, counter-clockwise seen from the eye.
     * 前窗四个角（顶点空间），从眼点看为逆时针。
     */
    public static Vector3f[] frontWindowCorners() {
        return new Vector3f[]{
                new Vector3f(WINDOW_X0, WINDOW_Y0, WINDOW_FRONT_Z).div(UNITS_PER_BLOCK),
                new Vector3f(WINDOW_X1, WINDOW_Y0, WINDOW_FRONT_Z).div(UNITS_PER_BLOCK),
                new Vector3f(WINDOW_X1, WINDOW_Y1, WINDOW_FRONT_Z).div(UNITS_PER_BLOCK),
                new Vector3f(WINDOW_X0, WINDOW_Y1, WINDOW_FRONT_Z).div(UNITS_PER_BLOCK)};
    }

    /**
     * Whether the dot's centre projects inside the projected front window. {@code clipFromSight} maps sight vertex space
     * to clip space (projection x model-view x base x the sight pose); every point must be in front of the camera
     * (w > 0). Any affine image of the screen (aspect, FOV) keeps the answer, so NDC is tested directly.
     * 红点中心的投影是否落在前窗投影之内。{@code clipFromSight} 把瞄具顶点空间映射到裁剪空间（投影 x 模型视图 x 基准 x 瞄具姿态）；
     * 每个点都必须位于相机前方（w > 0）。屏幕的任意仿射变换（宽高比、视场角）都不改变结果，因此直接在 NDC 中检测。
     */
    public static boolean dotInWindow(Matrix4fc clipFromSight) {
        float[] dot = ndc(clipFromSight, dotCenter());
        if (dot == null) {
            return false;
        }
        Vector3f[] corners = frontWindowCorners();
        float[][] projected = new float[corners.length][];
        for (int i = 0; i < corners.length; i++) {
            projected[i] = ndc(clipFromSight, corners[i]);
            if (projected[i] == null) {
                return false;
            }
        }
        // Inside a convex polygon: the dot is on the same side of every edge (either winding).
        // 位于凸多边形内：红点在每条边的同一侧（任一绕序）。
        int sign = 0;
        for (int i = 0; i < projected.length; i++) {
            float[] a = projected[i];
            float[] b = projected[(i + 1) % projected.length];
            float cross = (b[0] - a[0]) * (dot[1] - a[1]) - (b[1] - a[1]) * (dot[0] - a[0]);
            int side = cross > 0.0F ? 1 : cross < 0.0F ? -1 : 0;
            if (side == 0) {
                return false;
            }
            if (sign == 0) {
                sign = side;
            } else if (side != sign) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private static float[] ndc(Matrix4fc clip, Vector3f point) {
        Vector4f v = clip.transform(new Vector4f(point, 1.0F));
        if (!(v.w > MIN_W) || !Float.isFinite(v.x) || !Float.isFinite(v.y)) {
            return null;
        }
        return new float[]{v.x / v.w, v.y / v.w};
    }

    /** The cube root of the 3x3 determinant; NaN or non-positive for a mirror or degenerate pose. / 均匀缩放。 */
    static float uniformScale(Matrix4fc matrix) {
        if (!matrix.isFinite()) {
            return Float.NaN;
        }
        float det = matrix.determinant3x3();
        return det > 0.0F ? (float) Math.cbrt(det) : Float.NaN;
    }

    private static Matrix4f sightTiltModel(float tilt) {
        return new Matrix4f().translation(PIVOT.x(), PIVOT.y(), PIVOT.z()).rotateX(-tilt)
                .translate(-PIVOT.x(), -PIVOT.y(), -PIVOT.z());
    }

    private static boolean usableFov(double degrees) {
        return Double.isFinite(degrees) && degrees > 0.0 && degrees < 180.0;
    }

    private static float clamp01(float value) {
        return value >= 0.0F ? Math.min(1.0F, value) : 0.0F;
    }
}
