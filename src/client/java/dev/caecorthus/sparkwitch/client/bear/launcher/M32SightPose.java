package dev.caecorthus.sparkwitch.client.bear.launcher;

import dev.caecorthus.sparkwitch.roles.civilian.bear.launcher.M32Ballistics;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure maths of the M32A1's 3-D M2A1 red-dot aim (SIGHT-CONTRACT v3, 2026-11-06; v5 art the same week: a square
 * housing on a left quadrant bracket): the aim progress and its ease, the hand FOV, the range tilt, the full-aim pose,
 * the hip-to-aim blend, the draw correction, the dot's window test against the housing's square front aperture, and the
 * quadrant-grid reticle on the dot plane. No Minecraft types, so every number is unit-tested.
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
 * {@link #sightTilt} (nose down about +x through the pivot, the quadrant bracket's pin). {@link #blend} moves the hip
 * pose to it: the anchor's image is lerped, the rotation slerped and the uniform scale lerped.
 * <p>
 * The window is the housing's open square bore: a regular {@link #WINDOW_SIDES}-gon (a square, flats up, down and to
 * the sides) of inner apothem {@link #WINDOW_APOTHEM} about the sight axis (the eye's x and y), limited by the front
 * hood's lens aperture at {@link #WINDOW_FRONT_Z} and open back to the housing's rear face {@link #WINDOW_REAR_Z}. The
 * reticle ({@link #emitReticle}) lies on the dot plane {@link #DOT_Z}, inside {@link #RETICLE_CLIP_RADIUS}.
 * M32A1 三维 M2A1 红点瞄准的纯数学部分（SIGHT-CONTRACT v3，2026-11-06；同周的 v5 美术：方形机匣装在左侧象限托架上）：瞄准
 * 进度与缓动、手部视场角、射程俯仰、完全瞄准姿态、腰射到瞄准的过渡、绘制校正、红点相对机匣方形前孔的窗口检测，以及红点平面上的
 * 象限网格分划。不含 Minecraft 类型，因此每个数值都有单元测试。
 * <p>
 * 坐标空间。模型单位：1 = 64x64 贴图的一个像素，-z 为炮口，+x 为发射器右侧，+y 向上（美术 JSON）。顶点空间：模型单位 / 16
 * （烘焙四边形中的坐标；{@code ItemRenderer} 在显示变换之后再平移 -0.5）。基准空间：在第一人称
 * {@code HeldItemRenderer#renderItem(float, ...)} 的 HEAD 捕获的手部渲染矩阵（{@code B}）；它已包含受伤倾斜与视角摇晃，且该渲染
 * 的模型视图矩阵抵消了其中的相机旋转，因此基准空间即相机空间（+x 右、+y 上、-z 前）。这里的每个姿态都把顶点空间映射到基准空间。
 * <p>
 * 完全瞄准姿态 {@link #aimPose} 保持相机朝向与第一人称缩放 {@link #AIM_SCALE}，并把俯仰后的眼点
 * {@code P + Rx(-tilt)(E - P)} 放在原点。瞄具模型为该姿态乘以 {@link #sightTilt}（绕经过枢轴，即象限托架销轴的 +x 轴
 * 低头）。{@link #blend} 把腰射姿态移向它：锚点的像做线性插值，旋转做球面插值，均匀缩放做线性插值。
 * <p>
 * 窗口即机匣的开放方形内孔：绕瞄具轴（眼点的 x 与 y）、内切半径 {@link #WINDOW_APOTHEM} 的正 {@link #WINDOW_SIDES} 边形
 * （正方形，上下左右为平边），由 {@link #WINDOW_FRONT_Z} 处前遮光罩的镜孔限定，向后一直开放到机匣后端面
 * {@link #WINDOW_REAR_Z}。分划（{@link #emitReticle}）位于红点平面 {@link #DOT_Z}，且在 {@link #RETICLE_CLIP_RADIUS} 之内。
 */
public final class M32SightPose {
    /** Model units per block (vertex space = model / 16). / 每格的模型单位数。 */
    public static final float UNITS_PER_BLOCK = 16.0F;
    /**
     * Eye point on the sight axis, just inside the rubber eyecup (1/8 behind the housing's rear face at z 3.5), so the
     * front aperture fills about 55 % of the aimed view's height and the housing frames it; model units.
     * 瞄具轴上的眼点，位于橡胶眼罩内侧（机匣后端面 z 3.5 之后 1/8），使前孔约占瞄准画面高度的 55%，机匣围成边框（模型单位）。
     */
    public static final Vector3fc EYE = new Vector3f(8.0F, 21.75F, 3.625F);
    /**
     * Range-tilt pivot, model units: the quadrant bracket's pin at the centre of the engraved range arc (front of the
     * left plate, below the housing's front half), so the range lever at the plate's rear edge sweeps the arc.
     * 射程俯仰枢轴（模型单位）：象限托架的销轴，即刻度弧的圆心（左侧板前部、机匣前半部下方），使板后缘的射程拨杆沿弧扫过。
     */
    public static final Vector3fc PIVOT = new Vector3f(8.0F, 20.25F, -3.25F);
    /**
     * The open bore of the square housing, model units: a square about the sight axis (the eye's x and y) with inner
     * apothem {@link #WINDOW_APOTHEM} (half the lens aperture of the front hood; the housing walls behind it sit at
     * 1.875), corners at 45 + k x 90 degrees from the top, from the front aperture {@link #WINDOW_FRONT_Z} back to the
     * housing's rear face {@link #WINDOW_REAR_Z}. Nothing of the model lies inside it or on the axis in front of it.
     * 方形机匣的开放内孔（模型单位）：绕瞄具轴（眼点的 x 与 y）、内切半径 {@link #WINDOW_APOTHEM}（前遮光罩镜孔的半宽；其后的
     * 机匣内壁位于 1.875）的正方形，各角位于自顶部起 45 + k x 90 度，从前孔 {@link #WINDOW_FRONT_Z} 向后到机匣后端面
     * {@link #WINDOW_REAR_Z}。模型的任何部分都不在其内部，也不在其前方的轴线上。
     */
    public static final int WINDOW_SIDES = 4;
    public static final float WINDOW_APOTHEM = 1.75F;
    public static final float WINDOW_FRONT_Z = -4.125F;
    public static final float WINDOW_REAR_Z = 3.5F;
    /** The dot's plane on the sight axis (70 units in front of the eye). / 红点所在平面（眼点前方 70 单位）。 */
    public static final float DOT_Z = -66.375F;
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

    // ---- Quadrant-grid reticle on the dot plane (SIGHT-CONTRACT v3); model units on the plane unless noted ----
    // ---- 红点平面上的象限网格分划（SIGHT-CONTRACT v3）；除另有说明外均为平面上的模型单位 ----
    /** Eye to dot plane (70): a hand angle a lands at 70 tan(a) from the axis. / 眼点到红点平面的距离。 */
    public static final float RETICLE_DISTANCE = EYE.z() - DOT_Z;
    /**
     * The clip circle about the axis: the front aperture's inscribed circle seen from the eye, times a 0.93 margin
     * (about 14.70). Lines are clipped to it exactly; a tick or label draws only when its whole quad is inside.
     * 绕轴的裁剪圆：自眼点看到的前孔内切圆，再乘以 0.93 的余量（约 14.70）。线条精确裁剪到圆上；刻度或数字只有整个四边形都在圆内
     * 时才绘制。
     */
    public static final float RETICLE_CLIP_RADIUS = WINDOW_APOTHEM * RETICLE_DISTANCE / (EYE.z() - WINDOW_FRONT_Z)
            * 0.93F;
    /** Range ticks: half-lengths (major = every 50 m) and nominal thickness. / 射程刻度：半长（每 50 米为主刻度）与名义粗细。 */
    public static final float RANGE_TICK_MAJOR_HALF = 0.67F;
    public static final float RANGE_TICK_MINOR_HALF = 0.39F;
    public static final int RANGE_MAJOR_METRES = 50;
    public static final float TICK_THICKNESS = 0.14F;
    /**
     * Range labels (majors only): left edge this far right of the tick end, glyph height, width and advance.
     * 射程数字（仅主刻度）：左缘距刻度末端的距离、字形高度、宽度与步进。
     */
    public static final float LABEL_GAP = 0.30F;
    public static final float GLYPH_HEIGHT = 0.72F;
    public static final float GLYPH_WIDTH = 0.36F;
    public static final float GLYPH_ADVANCE = 0.44F;
    /** Lead ticks at +-1..3 hand degrees: half-lengths (odd / even degrees). / 提前量刻度（手部 ±1..3 度）：半长（奇 / 偶度）。 */
    public static final int LEAD_TICK_MAX_DEGREES = 3;
    public static final float LEAD_TICK_ODD_HALF = 0.27F;
    public static final float LEAD_TICK_EVEN_HALF = 0.49F;
    /** Cross lines: the gap round the dot and the nominal thickness. / 十字线：红点周围的间隙与名义粗细。 */
    public static final float CROSS_GAP = 0.92F;
    public static final float CROSS_THICKNESS = 0.07F;
    /** Element alphas before visibility; the vertex RGB is red (255, 48, 36). / 可见度之前的元素透明度；顶点颜色为红色。 */
    public static final float TICK_ALPHA = 0.9F;
    public static final float CROSS_ALPHA = 0.65F;
    public static final int RETICLE_RGB = 0xFF3024;
    /**
     * Atlas {@code m2a1_reticle.png} in texels (128 x 32): the bar (u 0..8, v 0..16; constant along u, opaque in the
     * middle half of v, so bar quads are drawn {@link #BAR_DRAW_SCALE} times their nominal thickness with v across it;
     * sampled at u 2..6 so the linear filter never reaches a neighbouring cell) and digit k's cell (u 8 + 12k .. 20 +
     * 12k, v 4..28; the glyph quad is the whole cell).
     * 分划贴图 {@code m2a1_reticle.png}（像素，128 x 32）：线条区（u 0..8、v 0..16；沿 u 不变，v 的中间一半不透明，因此线条四边形按
     * 名义粗细的 {@link #BAR_DRAW_SCALE} 倍绘制且 v 横跨粗细方向；在 u 2..6 采样，使线性过滤不会采到相邻格）与数字 k 的格子
     * （u 8 + 12k .. 20 + 12k、v 4..28；字形四边形即整个格子）。
     */
    public static final int ATLAS_WIDTH = 128;
    public static final int ATLAS_HEIGHT = 32;
    public static final float BAR_DRAW_SCALE = 2.0F;
    public static final int BAR_U0 = 2;
    public static final int BAR_U1 = 6;
    public static final int BAR_V0 = 0;
    public static final int BAR_V1 = 16;
    public static final int DIGIT_U0 = 8;
    public static final int DIGIT_CELL_WIDTH = 12;
    public static final int DIGIT_V0 = 4;
    public static final int DIGIT_V1 = 28;
    private static final float MIN_W = 1.0E-4F;
    /** The front aperture's corners, vertex space (read only). / 前孔各角（顶点空间，只读）。 */
    private static final Vector3fc[] FRONT_WINDOW = buildFrontWindow();

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
     * The front aperture's {@link #WINDOW_SIDES} corners in vertex space, counter-clockwise seen from the eye: the
     * square at {@link #WINDOW_FRONT_Z}, radius {@code WINDOW_APOTHEM / cos(180 deg / WINDOW_SIDES)} about the axis, at
     * (k + 1/2) x 360 / WINDOW_SIDES degrees from the top. Fresh copies.
     * 前孔的 {@link #WINDOW_SIDES} 个角（顶点空间），从眼点看为逆时针：位于 {@link #WINDOW_FRONT_Z} 的正方形，绕轴半径
     * {@code WINDOW_APOTHEM / cos(180 度 / WINDOW_SIDES)}，位于自顶部起 (k + 1/2) x 360 / WINDOW_SIDES 度。返回新副本。
     */
    public static Vector3f[] frontWindowCorners() {
        Vector3f[] corners = new Vector3f[FRONT_WINDOW.length];
        for (int i = 0; i < corners.length; i++) {
            corners[i] = new Vector3f(FRONT_WINDOW[i]);
        }
        return corners;
    }

    /**
     * Whether the dot's centre projects inside the projected front aperture. {@code clipFromSight} maps sight vertex
     * space to clip space (projection x model-view x base x the sight pose); every point must be in front of the camera
     * (w > 0). Any affine image of the screen (aspect, FOV) keeps the answer, so NDC is tested directly.
     * 红点中心的投影是否落在前孔投影之内。{@code clipFromSight} 把瞄具顶点空间映射到裁剪空间（投影 x 模型视图 x 基准 x 瞄具姿态）；
     * 每个点都必须位于相机前方（w > 0）。屏幕的任意仿射变换（宽高比、视场角）都不改变结果，因此直接在 NDC 中检测。
     */
    public static boolean dotInWindow(Matrix4fc clipFromSight) {
        Vector4f scratch = new Vector4f();
        float[] points = new float[2 * (FRONT_WINDOW.length + 1)];
        if (!ndc(clipFromSight, EYE.x() / UNITS_PER_BLOCK, EYE.y() / UNITS_PER_BLOCK, DOT_Z / UNITS_PER_BLOCK, scratch,
                points, 0)) {
            return false;
        }
        for (int i = 0; i < FRONT_WINDOW.length; i++) {
            Vector3fc corner = FRONT_WINDOW[i];
            if (!ndc(clipFromSight, corner.x(), corner.y(), corner.z(), scratch, points, 2 * (i + 1))) {
                return false;
            }
        }
        // Inside a convex polygon: the dot is on the same side of every edge (either winding).
        // 位于凸多边形内：红点在每条边的同一侧（任一绕序）。
        float dotX = points[0];
        float dotY = points[1];
        int sign = 0;
        for (int i = 0; i < FRONT_WINDOW.length; i++) {
            int a = 2 * (i + 1);
            int b = 2 * ((i + 1) % FRONT_WINDOW.length + 1);
            float cross = (points[b] - points[a]) * (dotY - points[a + 1])
                    - (points[b + 1] - points[a + 1]) * (dotX - points[a]);
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

    /**
     * The reticle's visibility: smoothstep(0.75, 1, p), so it never floats beside the frame mid-transition. The dot's
     * own alpha is 1, so this is the whole factor.
     * 分划的可见度：smoothstep(0.75, 1, p)，过渡中途它不会飘在镜框之外。红点自身透明度为 1，因此这就是全部系数。
     */
    public static float reticleAlpha(float progress) {
        float t = clamp01((clamp01(progress) - 0.75F) / 0.25F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** Vertex colour: {@link #RETICLE_RGB} at the clamped alpha. / 顶点颜色：钳制透明度下的 {@link #RETICLE_RGB}。 */
    public static int reticleArgb(float alpha) {
        return Math.round(clamp01(alpha) * 255.0F) << 24 | RETICLE_RGB;
    }

    /**
     * Receives one axis-aligned reticle quad on the dot plane ({@code z = DOT_Z / 16}), vertex space, {@code x0 < x1},
     * {@code y0 < y1}; atlas UVs normalised. {@code vAlongX == false}: u runs x0 to x1 as u0 to u1 and v runs top
     * (y1) to bottom (y0) as v0 to v1. {@code vAlongX == true} (vertical bars): v runs x0 to x1 as v0 to v1 and u runs
     * top to bottom as u0 to u1. {@code alpha} is the element's alpha before {@link #reticleAlpha}.
     * 接收红点平面（{@code z = DOT_Z / 16}）上的一个轴对齐分划四边形（顶点空间，{@code x0 < x1}、{@code y0 < y1}；贴图 UV 已归一化）。
     * {@code vAlongX == false}：u 从 x0 到 x1 对应 u0 到 u1，v 从上（y1）到下（y0）对应 v0 到 v1。{@code vAlongX == true}（竖直线条）：
     * v 从 x0 到 x1 对应 v0 到 v1，u 从上到下对应 u0 到 u1。{@code alpha} 为乘以 {@link #reticleAlpha} 之前的元素透明度。
     */
    @FunctionalInterface
    public interface ReticleSink {
        void quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, boolean vAlongX,
                  float alpha);
    }

    /** One quad as {@link ReticleSink} receives it (tests and tools). / {@link ReticleSink} 接收的一个四边形。 */
    public record ReticleQuad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
                              boolean vAlongX, float alpha) {
    }

    /**
     * The quadrant-grid reticle in the sight's vertex space, emitted without allocating per quad: the four cross lines
     * (a {@link #CROSS_GAP} gap round the dot, clipped exactly to {@link #RETICLE_CLIP_RADIUS}: their outer corners lie
     * on it), lead ticks at +-1..{@link #LEAD_TICK_MAX_DEGREES} hand degrees on the horizontal axis, then one range tick
     * per dial setting r except the dialled one at ay = h'(r) - h'(dial) below the axis
     * (h' = {@link #tiltRadians}({@link M32Ballistics#holdoverRadians}), the dot's own solve; unsolved settings are
     * skipped), majors followed by their digits. Ticks and labels are emitted only when their whole quad (a label: all
     * its glyphs) is inside the clip circle. Nothing when the dialled setting has no solution.
     * 瞄具顶点空间中的象限网格分划，逐个四边形输出且不为每个四边形分配对象：四条十字线（红点周围留 {@link #CROSS_GAP} 间隙，精确
     * 裁剪到 {@link #RETICLE_CLIP_RADIUS}：外侧两角位于圆上）、水平轴上手部 ±1..{@link #LEAD_TICK_MAX_DEGREES} 度的提前量刻度，然后
     * 除当前刻度外每个刻度值 r 一条射程刻度，位于轴下方 ay = h'(r) - h'(当前刻度) 处（h' = {@link #tiltRadians}(
     * {@link M32Ballistics#holdoverRadians})，与红点同一求解；无解的刻度值跳过），主刻度后接其数字。刻度与数字只有整个四边形（数字为
     * 其全部字形）都在裁剪圆内时才输出。当前刻度无解时不输出任何内容。
     */
    public static void emitReticle(double pitchDegrees, int dialMetres, double handFovDegrees, double worldFovDegrees,
                                   ReticleSink sink) {
        double dialTilt = tiltRadians(M32Ballistics.holdoverRadians(pitchDegrees, dialMetres), handFovDegrees,
                worldFovDegrees);
        if (!Double.isFinite(dialTilt)) {
            return;
        }
        float cx = EYE.x();
        float cy = EYE.y();
        float radius = RETICLE_CLIP_RADIUS;
        float lineHalf = CROSS_THICKNESS * BAR_DRAW_SCALE / 2.0F;
        float lineEnd = (float) Math.sqrt(radius * radius - lineHalf * lineHalf);
        bar(sink, cx + CROSS_GAP, cy - lineHalf, cx + lineEnd, cy + lineHalf, false, CROSS_ALPHA);
        bar(sink, cx - lineEnd, cy - lineHalf, cx - CROSS_GAP, cy + lineHalf, false, CROSS_ALPHA);
        bar(sink, cx - lineHalf, cy + CROSS_GAP, cx + lineHalf, cy + lineEnd, true, CROSS_ALPHA);
        bar(sink, cx - lineHalf, cy - lineEnd, cx + lineHalf, cy - CROSS_GAP, true, CROSS_ALPHA);
        float tickHalf = TICK_THICKNESS * BAR_DRAW_SCALE / 2.0F;
        for (int degrees = -LEAD_TICK_MAX_DEGREES; degrees <= LEAD_TICK_MAX_DEGREES; degrees++) {
            if (degrees == 0) {
                continue;
            }
            float x = cx + (float) (RETICLE_DISTANCE * Math.tan(Math.toRadians(degrees)));
            float half = (degrees & 1) != 0 ? LEAD_TICK_ODD_HALF : LEAD_TICK_EVEN_HALF;
            if (insideClip(x - tickHalf, cy - half, x + tickHalf, cy + half)) {
                bar(sink, x - tickHalf, cy - half, x + tickHalf, cy + half, true, TICK_ALPHA);
            }
        }
        for (int metres : M32Ballistics.dialSettings()) {
            if (metres == dialMetres) {
                continue;
            }
            double tilt = tiltRadians(M32Ballistics.holdoverRadians(pitchDegrees, metres), handFovDegrees,
                    worldFovDegrees);
            if (!Double.isFinite(tilt)) {
                continue;
            }
            float y = cy - (float) (RETICLE_DISTANCE * Math.tan(tilt - dialTilt));
            boolean major = metres % RANGE_MAJOR_METRES == 0;
            float half = major ? RANGE_TICK_MAJOR_HALF : RANGE_TICK_MINOR_HALF;
            if (!insideClip(cx - half, y - tickHalf, cx + half, y + tickHalf)) {
                continue;
            }
            bar(sink, cx - half, y - tickHalf, cx + half, y + tickHalf, false, TICK_ALPHA);
            if (major) {
                label(sink, metres, cx + half + LABEL_GAP, y);
            }
        }
    }

    /** {@link #emitReticle} collected into a list. / 收集为列表的 {@link #emitReticle}。 */
    public static List<ReticleQuad> reticle(double pitchDegrees, int dialMetres, double handFovDegrees,
                                            double worldFovDegrees) {
        List<ReticleQuad> quads = new ArrayList<>();
        emitReticle(pitchDegrees, dialMetres, handFovDegrees, worldFovDegrees,
                (x0, y0, x1, y1, u0, v0, u1, v1, vAlongX, alpha)
                        -> quads.add(new ReticleQuad(x0, y0, x1, y1, u0, v0, u1, v1, vAlongX, alpha)));
        return quads;
    }

    /** {@code metres} in digit glyphs from {@code left}, centred on {@code y}, or nothing unless all fit. / 数字标签。 */
    private static void label(ReticleSink sink, int metres, float left, float y) {
        int digits = 1;
        int divisor = 1;
        while (divisor <= metres / 10) {
            divisor *= 10;
            digits++;
        }
        float y0 = y - GLYPH_HEIGHT / 2.0F;
        float y1 = y + GLYPH_HEIGHT / 2.0F;
        if (!insideClip(left, y0, left + (digits - 1) * GLYPH_ADVANCE + GLYPH_WIDTH, y1)) {
            return;
        }
        float x = left;
        for (; divisor > 0; divisor /= 10, x += GLYPH_ADVANCE) {
            int digit = metres / divisor % 10;
            float u0 = DIGIT_U0 + DIGIT_CELL_WIDTH * digit;
            emit(sink, x, y0, x + GLYPH_WIDTH, y1, u0 / ATLAS_WIDTH, (float) DIGIT_V0 / ATLAS_HEIGHT,
                    (u0 + DIGIT_CELL_WIDTH) / ATLAS_WIDTH, (float) DIGIT_V1 / ATLAS_HEIGHT, false, TICK_ALPHA);
        }
    }

    private static void bar(ReticleSink sink, float x0, float y0, float x1, float y1, boolean vAlongX, float alpha) {
        emit(sink, x0, y0, x1, y1, (float) BAR_U0 / ATLAS_WIDTH, (float) BAR_V0 / ATLAS_HEIGHT,
                (float) BAR_U1 / ATLAS_WIDTH, (float) BAR_V1 / ATLAS_HEIGHT, vAlongX, alpha);
    }

    /** Model units on the plane to vertex space. / 平面上的模型单位转为顶点空间。 */
    private static void emit(ReticleSink sink, float x0, float y0, float x1, float y1, float u0, float v0, float u1,
                             float v1, boolean vAlongX, float alpha) {
        sink.quad(x0 / UNITS_PER_BLOCK, y0 / UNITS_PER_BLOCK, x1 / UNITS_PER_BLOCK, y1 / UNITS_PER_BLOCK, u0, v0, u1, v1,
                vAlongX, alpha);
    }

    /** All four corners inside the clip circle (a convex set, so the whole box is). / 四角都在裁剪圆内。 */
    private static boolean insideClip(float x0, float y0, float x1, float y1) {
        float dx = Math.max(Math.abs(x0 - EYE.x()), Math.abs(x1 - EYE.x()));
        float dy = Math.max(Math.abs(y0 - EYE.y()), Math.abs(y1 - EYE.y()));
        return dx * dx + dy * dy <= RETICLE_CLIP_RADIUS * RETICLE_CLIP_RADIUS;
    }

    private static Vector3fc[] buildFrontWindow() {
        Vector3fc[] corners = new Vector3fc[WINDOW_SIDES];
        double step = 2.0 * Math.PI / WINDOW_SIDES;
        double radius = WINDOW_APOTHEM / Math.cos(step / 2.0);
        for (int k = 0; k < WINDOW_SIDES; k++) {
            // From the top towards -x: counter-clockwise seen from the eye (looking along -z, +x right).
            // 自顶部向 -x 方向：从眼点看为逆时针（沿 -z 看，+x 在右）。
            double angle = step / 2.0 + k * step;
            corners[k] = new Vector3f((float) (EYE.x() - radius * Math.sin(angle)),
                    (float) (EYE.y() + radius * Math.cos(angle)), WINDOW_FRONT_Z).div(UNITS_PER_BLOCK);
        }
        return corners;
    }

    private static boolean ndc(Matrix4fc clip, float x, float y, float z, Vector4f scratch, float[] out, int at) {
        Vector4f v = clip.transform(scratch.set(x, y, z, 1.0F));
        if (!(v.w > MIN_W) || !Float.isFinite(v.x) || !Float.isFinite(v.y)) {
            return false;
        }
        out[at] = v.x / v.w;
        out[at + 1] = v.y / v.w;
        return true;
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
