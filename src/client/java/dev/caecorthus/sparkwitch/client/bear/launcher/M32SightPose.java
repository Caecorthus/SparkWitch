package dev.caecorthus.sparkwitch.client.bear.launcher;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

/**
 * Pure maths of the M32A1's 3-D M2A1 reflex aim (pixel-style art v7, 2026-11-11: the round rail-shrouded tube on a
 * two-plate cradle, after the Milkor sight as Escape from Tarkov shows it): the aim progress and its ease, the hand FOV,
 * the range tilt, the full-aim pose, the hip-to-aim blend, the draw correction, the reticle's window test against the
 * tube's front aperture, and the reticle quad on its plane. No Minecraft types, so every number is unit-tested.
 * <p>
 * Spaces. Model units: the art JSON's own units (16 to a block), -z muzzle, +x the launcher's right, +y up.
 * Vertex space: model units / 16 (what a baked quad holds; {@code ItemRenderer} translates it by -0.5 after the
 * display transform). Base space: the hand-pass matrix captured at HEAD of the first-person
 * {@code HeldItemRenderer#renderItem(float, ...)} ({@code B}); it already carries hurt tilt and view bob, and the
 * pass's model-view undoes its camera rotation, so base space is camera space (+x right, +y up, -z forward). Every
 * pose here maps vertex space to base space.
 * <p>
 * The full aim puts the SIGHT on the camera (owner 2026-11-11: the sight stays in the middle of the screen when the
 * range goes up, the launcher noses up under it): the sight's axis is the view axis and its eye point the origin, at
 * the first-person scale {@link #AIM_SCALE}, so the reticle sits at the screen centre. The body's pose
 * {@link #aimPose} is that turned nose up about the pivot by the range tilt, {@code S T(-E) sightTilt^-1}; the sight
 * model is the body pose times {@link #sightTilt} (nose down about +x through the pivot, the cradle's front pin),
 * which is {@code S T(-E)} again. {@link #blend} moves the hip pose to the body pose: the anchor's image is lerped,
 * the rotation slerped and the uniform scale lerped.
 * <p>
 * The window is the tube's round bore: a regular {@link #WINDOW_SIDES}-gon (flats up, down and to the sides) of inner
 * apothem {@link #WINDOW_APOTHEM} about the sight axis (the eye's x and y), limited by the tube's front end at
 * {@link #WINDOW_FRONT_Z} and open back to the eyecup's rear face {@link #WINDOW_REAR_Z}. The reticle is one quad on
 * the plane {@link #RETICLE_Z}, centred on the axis ({@link #reticleQuad}).
 * M32A1 三维 M2A1 反射式瞄准的纯数学部分（像素风美术 v7，2026-11-11：导轨包覆的圆筒装在双侧板托架上，参照《逃离塔科夫》中的
 * Milkor 瞄具）：瞄准进度与缓动、手部视场角、射程俯仰、完全瞄准姿态、腰射到瞄准的过渡、绘制校正、分划相对镜筒前孔的窗口检测，
 * 以及分划平面上的分划四边形。不含 Minecraft 类型，因此每个数值都有单元测试。
 * <p>
 * 坐标空间。模型单位：美术 JSON 自身的单位（每格 16），-z 为炮口，+x 为发射器右侧，+y 向上。顶点空间：模型单位 / 16
 * （烘焙四边形中的坐标；{@code ItemRenderer} 在显示变换之后再平移 -0.5）。基准空间：在第一人称
 * {@code HeldItemRenderer#renderItem(float, ...)} 的 HEAD 捕获的手部渲染矩阵（{@code B}）；它已包含受伤倾斜与视角摇晃，且该渲染
 * 的模型视图矩阵抵消了其中的相机旋转，因此基准空间即相机空间（+x 右、+y 上、-z 前）。这里的每个姿态都把顶点空间映射到基准空间。
 * <p>
 * 完全瞄准时瞄具对准相机（所有者 2026-11-11：调高射程时镜子留在屏幕正中，发射器在它下面抬头）：瞄具轴线即视线，其眼点位于
 * 原点，缩放为第一人称缩放 {@link #AIM_SCALE}，因此分划位于屏幕中心。机身姿态 {@link #aimPose} 是在此基础上绕枢轴抬头一个射程
 * 俯仰角，即 {@code S T(-E) sightTilt^-1}；瞄具模型为机身姿态乘以 {@link #sightTilt}（绕经过枢轴，即托架前销轴的 +x 轴低头），
 * 结果仍是 {@code S T(-E)}。{@link #blend} 把腰射姿态移向机身姿态：锚点的像做线性插值，旋转做球面插值，均匀缩放做线性插值。
 * <p>
 * 窗口即镜筒的圆形内孔：绕瞄具轴（眼点的 x 与 y）、内切半径 {@link #WINDOW_APOTHEM} 的正 {@link #WINDOW_SIDES} 边形
 * （上下左右为平边），由 {@link #WINDOW_FRONT_Z} 处的镜筒前端限定，向后一直开放到眼罩后端面 {@link #WINDOW_REAR_Z}。
 * 分划是平面 {@link #RETICLE_Z} 上以轴线为中心的一个四边形（{@link #reticleQuad}）。
 */
public final class M32SightPose {
    /** Model units per block (vertex space = model / 16). / 每格的模型单位数。 */
    public static final float UNITS_PER_BLOCK = 16.0F;
    /**
     * Eye point on the sight axis at a long eye relief, 24 units behind the eyecup's rear face at z 4.5 (the cheek on
     * the stock). At the {@link #AIM_HAND_FOV_DEGREES} hand FOV the bore is a small reflex window of about 13 % of the
     * aimed view's height, the eyepiece ring about 26 % and the three-eared rear plate about 44 %, so the sight reads
     * as a red dot on a launcher and the world stays visible round it (owner 2026-11-06: "it is just a red dot";
     * 2026-11-11: sight and sight picture redone after the Tarkov M2A1); model units.
     * 瞄具轴上的眼点，取较长的眼距：眼罩后端面 z 4.5 之后 24 单位（脸颊贴枪托）。在 {@link #AIM_HAND_FOV_DEGREES} 手部视场角下，
     * 内孔是约占瞄准画面高度 13% 的小窗口，目镜环约 26%，三耳后挡板约 44%，因此看起来就是发射器上的红点镜，周围仍能看到世界
     * （所有者 2026-11-06：“人家就是个红点”；2026-11-11：参照塔科夫的 M2A1 重做瞄具与镜内画面）（模型单位）。
     */
    public static final Vector3fc EYE = new Vector3f(8.0F, 21.5F, 28.5F);
    /**
     * Range-tilt pivot, model units: the cradle's front pin between the two side plates, below the tube's front half,
     * so relative to the launcher the rear of the sight lifts as the range goes up and the white pointer on the left
     * climbs the dotted scale on the plate's rear edge.
     * 射程俯仰枢轴（模型单位）：两侧板之间的托架前销轴，位于镜筒前半部下方，因此射程增大时瞄具后端相对发射器抬起，左侧的白色
     * 指针沿侧板后缘的点状刻度上升。
     */
    public static final Vector3fc PIVOT = new Vector3f(8.0F, 19.0F, -1.25F);
    /**
     * The tube's round bore, model units: a regular 16-gon about the sight axis (the eye's x and y) with inner apothem
     * {@link #WINDOW_APOTHEM} (the 16 tube staves), corners at 11.25 + k x 22.5 degrees from the top, from the tube's
     * front end {@link #WINDOW_FRONT_Z} back to the eyecup's rear face {@link #WINDOW_REAR_Z}. Nothing of the sight
     * model ({@code item/m32a1_sight}) lies inside it or on the axis in front of it; the coated objective lens exists
     * only in the full model ({@code item/m32a1}), as one muzzle-facing quad the shooter never sees.
     * 镜筒的圆形内孔（模型单位）：绕瞄具轴（眼点的 x 与 y）、内切半径 {@link #WINDOW_APOTHEM}（16 条筒壁）的正十六边形，各角位于
     * 自顶部起 11.25 + k x 22.5 度，从镜筒前端 {@link #WINDOW_FRONT_Z} 向后到眼罩后端面 {@link #WINDOW_REAR_Z}。瞄具模型
     * （{@code item/m32a1_sight}）的任何部分都不在其内部，也不在其前方的轴线上；镀膜物镜只存在于完整模型
     * （{@code item/m32a1}）中，是一个朝向炮口、射手永远看不到的四边形。
     */
    public static final int WINDOW_SIDES = 16;
    public static final float WINDOW_APOTHEM = 1.25F;
    public static final float WINDOW_FRONT_Z = -4.0F;
    public static final float WINDOW_REAR_Z = 4.5F;
    /** The reticle's plane on the sight axis (70 units in front of the eye). / 分划所在平面（眼点前方 70 单位）。 */
    public static final float RETICLE_Z = -41.5F;
    /** Eye to reticle plane (70): a hand angle a lands at 70 tan(a) from the axis. / 眼点到分划平面的距离。 */
    public static final float RETICLE_DISTANCE = EYE.z() - RETICLE_Z;
    /**
     * Edge of the reticle quad on its plane, model units. The texture {@code m2a1_reticle.png} (128 x 128, colour
     * baked, linear filtering) holds the four double-barred arms round an empty centre inside its middle 72 texels, so
     * the mark itself spans about 1.6 units: 1.3 degrees from the eye and 30 % of the bore's width, as on the Tarkov
     * sight. The aim point is the empty centre.
     * 分划四边形在其平面上的边长（模型单位）。贴图 {@code m2a1_reticle.png}（128 x 128，颜色已烘焙，线性过滤）的中间 72 像素内是
     * 围绕空心中心的四条双横杠短臂，因此分划本身约宽 1.6 单位：自眼点看约 1.3 度，占内孔宽度的 30%，与塔科夫中的瞄具一致。
     * 瞄准点是空心的中心。
     */
    public static final float RETICLE_EDGE = 2.875F;
    /** First-person scale of the full aim pose (the display block's 0.5). / 完全瞄准姿态的第一人称缩放。 */
    public static final float AIM_SCALE = 0.5F;
    /**
     * Hand render FOV at full aim (narrow, so the pulled-back window still reads).
     * 完全瞄准时的手部渲染视场角（较窄，使后撤后的窗口仍清晰可读）。
     */
    public static final double AIM_HAND_FOV_DEGREES = 32.0;
    /** World zoom while aiming (1.25x) and its look scale. / 瞄准时的世界放大（1.25 倍）及视角缩放。 */
    public static final float WORLD_FOV_MULTIPLIER = 0.8F;
    public static final float LOOK_MULTIPLIER = 0.8F;
    public static final double AIM_IN_SECONDS = 0.20;
    public static final double AIM_OUT_SECONDS = 0.15;
    /** Fail-safe bound on the range tilt; the dial never needs more. / 射程俯仰的失败安全上限，刻度从不需要更多。 */
    public static final double MAX_TILT_RADIANS = Math.toRadians(30.0);
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
     * The tilt h' between sight and bore in the hand projection that shows the bore's elevation {@code h} (world
     * projection, the angle the round leaves above the line of sight) at the same screen row:
     * atan(tan(h) * tan(handFov / 2) / tan(worldFov / 2)). NaN for unusable inputs or past {@link #MAX_TILT_RADIANS}.
     * 手部投影中瞄具与枪膛之间的俯仰 h'，使枪膛抬高角 {@code h}（世界投影，榴弹出膛方向高于瞄准线的角度）落在同一屏幕行：
     * atan(tan(h) * tan(手部视场角 / 2) / tan(世界视场角 / 2))。输入不可用或超过 {@link #MAX_TILT_RADIANS} 时为 NaN。
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

    /** The eye point in the body's space, P + Rx(-tilt)(E - P), model units. / 机身空间中的眼点（模型单位）。 */
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
     * Full aim pose of the body, vertex space to base space: {@code S T(-E) sightTilt(tilt)^-1}. The sight (this times
     * {@link #sightTilt}) is camera-aligned with its eye point at the origin; the body is turned nose up about the pivot
     * by {@code tilt} radians, scale {@link #AIM_SCALE}.
     * 机身的完全瞄准姿态（顶点空间到基准空间）：{@code S T(-E) sightTilt(tilt)^-1}。瞄具（本姿态乘以 {@link #sightTilt}）与相机
     * 对齐、眼点位于原点；机身绕枢轴抬头 {@code tilt} 弧度，缩放为 {@link #AIM_SCALE}。
     */
    public static Matrix4f aimPose(float tilt) {
        Vector3f eye = new Vector3f(EYE).div(UNITS_PER_BLOCK);
        Vector3f pivot = new Vector3f(PIVOT).div(UNITS_PER_BLOCK);
        return new Matrix4f().scaling(AIM_SCALE).translate(-eye.x, -eye.y, -eye.z)
                .translate(pivot).rotateX(tilt).translate(-pivot.x, -pivot.y, -pivot.z);
    }

    /** The eye point in the body's vertex space, the blend's anchor. / 机身顶点空间中的眼点，即过渡锚点。 */
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

    /** The reticle's centre on the sight axis, vertex space. / 分划中心（瞄具轴上，顶点空间）。 */
    public static Vector3f reticleCenter() {
        return new Vector3f(EYE.x(), EYE.y(), RETICLE_Z).div(UNITS_PER_BLOCK);
    }

    /**
     * The front aperture's {@link #WINDOW_SIDES} corners in vertex space, counter-clockwise seen from the eye: the
     * polygon at {@link #WINDOW_FRONT_Z}, radius {@code WINDOW_APOTHEM / cos(180 deg / WINDOW_SIDES)} about the axis, at
     * (k + 1/2) x 360 / WINDOW_SIDES degrees from the top. Fresh copies.
     * 前孔的 {@link #WINDOW_SIDES} 个角（顶点空间），从眼点看为逆时针：位于 {@link #WINDOW_FRONT_Z} 的正多边形，绕轴半径
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
     * Whether the reticle's centre projects inside the projected front aperture. {@code clipFromSight} maps sight vertex
     * space to clip space (projection x model-view x base x the sight pose); every point must be in front of the camera
     * (w > 0). Any affine image of the screen (aspect, FOV) keeps the answer, so NDC is tested directly.
     * 分划中心的投影是否落在前孔投影之内。{@code clipFromSight} 把瞄具顶点空间映射到裁剪空间（投影 x 模型视图 x 基准 x 瞄具姿态）；
     * 每个点都必须位于相机前方（w > 0）。屏幕的任意仿射变换（宽高比、视场角）都不改变结果，因此直接在 NDC 中检测。
     */
    public static boolean reticleInWindow(Matrix4fc clipFromSight) {
        Vector4f scratch = new Vector4f();
        float[] points = new float[2 * (FRONT_WINDOW.length + 1)];
        if (!ndc(clipFromSight, EYE.x() / UNITS_PER_BLOCK, EYE.y() / UNITS_PER_BLOCK, RETICLE_Z / UNITS_PER_BLOCK, scratch,
                points, 0)) {
            return false;
        }
        for (int i = 0; i < FRONT_WINDOW.length; i++) {
            Vector3fc corner = FRONT_WINDOW[i];
            if (!ndc(clipFromSight, corner.x(), corner.y(), corner.z(), scratch, points, 2 * (i + 1))) {
                return false;
            }
        }
        // Inside a convex polygon: the centre is on the same side of every edge (either winding).
        // 位于凸多边形内：中心在每条边的同一侧（任一绕序）。
        float centerX = points[0];
        float centerY = points[1];
        int sign = 0;
        for (int i = 0; i < FRONT_WINDOW.length; i++) {
            int a = 2 * (i + 1);
            int b = 2 * ((i + 1) % FRONT_WINDOW.length + 1);
            float cross = (points[b] - points[a]) * (centerY - points[a + 1])
                    - (points[b + 1] - points[a + 1]) * (centerX - points[a]);
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
     * The reticle's visibility: smoothstep(0.75, 1, p), so it never floats beside the housing mid-transition.
     * 分划的可见度：smoothstep(0.75, 1, p)，过渡中途它不会飘在镜体之外。
     */
    public static float reticleAlpha(float progress) {
        float t = clamp01((clamp01(progress) - 0.75F) / 0.25F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** Vertex colour: white (the texture carries the colour) at the clamped alpha. / 顶点颜色：钳制透明度下的白色。 */
    public static int reticleArgb(float alpha) {
        return Math.round(clamp01(alpha) * 255.0F) << 24 | 0xFFFFFF;
    }

    /**
     * The reticle quad on the plane {@code z = RETICLE_Z / 16}, vertex space, {@code x0 < x1}, {@code y0 < y1}: the
     * square of edge {@link #RETICLE_EDGE} about the sight axis. The whole texture maps onto it, u along +x and v from
     * the top (y1) down.
     * 平面 {@code z = RETICLE_Z / 16} 上的分划四边形（顶点空间，{@code x0 < x1}、{@code y0 < y1}）：绕瞄具轴、边长
     * {@link #RETICLE_EDGE} 的正方形。整张贴图映射其上，u 沿 +x，v 自上（y1）向下。
     */
    public record ReticleQuad(float x0, float y0, float x1, float y1) {
    }

    /** {@link ReticleQuad} about the axis. / 绕轴线的 {@link ReticleQuad}。 */
    public static ReticleQuad reticleQuad() {
        float half = RETICLE_EDGE / 2.0F;
        return new ReticleQuad((EYE.x() - half) / UNITS_PER_BLOCK, (EYE.y() - half) / UNITS_PER_BLOCK,
                (EYE.x() + half) / UNITS_PER_BLOCK, (EYE.y() + half) / UNITS_PER_BLOCK);
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
