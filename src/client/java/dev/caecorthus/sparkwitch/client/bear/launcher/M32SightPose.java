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
 * the full-aim pose, the hip-to-aim blend, the draw correction, the reticle's window test against the tube's front
 * aperture, and the reticle on its plane: the centre mark and the fixed range scale under it. No Minecraft types, so
 * every number is unit-tested.
 * <p>
 * Spaces. Model units: the art JSON's own units (16 to a block), -z muzzle, +x the launcher's right, +y up.
 * Vertex space: model units / 16 (what a baked quad holds; {@code ItemRenderer} translates it by -0.5 after the
 * display transform). Base space: the hand-pass matrix captured at HEAD of the first-person
 * {@code HeldItemRenderer#renderItem(float, ...)} ({@code B}); it already carries hurt tilt and view bob, and the
 * pass's model-view undoes its camera rotation, so base space is camera space (+x right, +y up, -z forward). Every
 * pose here maps vertex space to base space.
 * <p>
 * The full aim puts the sight on the camera: the sight's axis is the view axis and its eye point the origin, at the
 * first-person scale {@link #AIM_SCALE}, so the centre mark sits at the screen centre ({@link #aimPose}). The whole
 * launcher shares that pose (owner 2026-11-11: there is no range dial, so nothing tilts). {@link #blend} moves the hip
 * pose to it: the anchor's image is lerped, the rotation slerped and the uniform scale lerped.
 * <p>
 * The window is the tube's round bore: a regular {@link #WINDOW_SIDES}-gon (flats up, down and to the sides) of inner
 * apothem {@link #WINDOW_APOTHEM} about the sight axis (the eye's x and y), limited by the tube's front end at
 * {@link #WINDOW_FRONT_Z} and open back to the eyecup's rear face {@link #WINDOW_REAR_Z}. The reticle lies on the plane
 * {@link #RETICLE_Z}, where the front aperture's image from the eye has apothem {@link #RETICLE_WINDOW_APOTHEM}.
 * <p>
 * Range scale (owner 2026-11-11: "no dial, let them shoot by the scale"; layout "centre direct fire, scale below").
 * The round leaves along the look, so the centre mark is the bore line. For each range the round is
 * {@code M32Ballistics.holdoverRadians} below the bore line as the shooter sees it; {@link #scaleOffset} turns that
 * world-projection angle into model units below the axis on the reticle plane for the live hand and world FOV, and the
 * mark is drawn there ({@link #tickRect}, {@link #labelRect}), joined to the centre by {@link #stemRect}. A mark is
 * drawn only while it fits the window ({@link #tickFits}, {@link #labelFits}) and keeps clear of the mark above it
 * ({@link #markClear}): 25, 50 and 75 m at the default FOV.
 * M32A1 三维 M2A1 反射式瞄准的纯数学部分（像素风美术 v7，2026-11-11：导轨包覆的圆筒装在双侧板托架上，参照《逃离塔科夫》中的
 * Milkor 瞄具）：瞄准进度与缓动、手部视场角、完全瞄准姿态、腰射到瞄准的过渡、绘制校正、分划相对镜筒前孔的窗口检测，
 * 以及分划平面上的分划：中心分划及其下方的固定射程刻度尺。不含 Minecraft 类型，因此每个数值都有单元测试。
 * <p>
 * 坐标空间。模型单位：美术 JSON 自身的单位（每格 16），-z 为炮口，+x 为发射器右侧，+y 向上。顶点空间：模型单位 / 16
 * （烘焙四边形中的坐标；{@code ItemRenderer} 在显示变换之后再平移 -0.5）。基准空间：在第一人称
 * {@code HeldItemRenderer#renderItem(float, ...)} 的 HEAD 捕获的手部渲染矩阵（{@code B}）；它已包含受伤倾斜与视角摇晃，且该渲染
 * 的模型视图矩阵抵消了其中的相机旋转，因此基准空间即相机空间（+x 右、+y 上、-z 前）。这里的每个姿态都把顶点空间映射到基准空间。
 * <p>
 * 完全瞄准时瞄具对准相机：瞄具轴线即视线，其眼点位于原点，缩放为第一人称缩放 {@link #AIM_SCALE}，因此中心分划位于屏幕中心
 * （{@link #aimPose}）。整支发射器共用这一姿态（所有者 2026-11-11：不设射程调节，因此没有任何部件俯仰）。{@link #blend} 把腰射
 * 姿态移向它：锚点的像做线性插值，旋转做球面插值，均匀缩放做线性插值。
 * <p>
 * 窗口即镜筒的圆形内孔：绕瞄具轴（眼点的 x 与 y）、内切半径 {@link #WINDOW_APOTHEM} 的正 {@link #WINDOW_SIDES} 边形
 * （上下左右为平边），由 {@link #WINDOW_FRONT_Z} 处的镜筒前端限定，向后一直开放到眼罩后端面 {@link #WINDOW_REAR_Z}。
 * 分划位于平面 {@link #RETICLE_Z}，自眼点看前孔在该平面上的像内切半径为 {@link #RETICLE_WINDOW_APOTHEM}。
 * <p>
 * 射程刻度尺（所有者 2026-11-11：“刻度不需要调，按刻度尺打”；排法为“中心直射＋下方刻度”）。榴弹沿视线射出，因此中心分划就是
 * 枪膛线。对每个射程，射手看到榴弹位于枪膛线下方 {@code M32Ballistics.holdoverRadians}；{@link #scaleOffset} 按实时的手部与世界
 * 视场角把这个世界投影中的角度换算为分划平面上轴线下方的模型单位，刻度就画在那里（{@link #tickRect}、{@link #labelRect}），并由
 * {@link #stemRect} 与中心相连。只有落在窗口内（{@link #tickFits}、{@link #labelFits}）且与上方刻度保持间距（{@link #markClear}）的
 * 刻度才绘制：默认视场角下为 25、50、75 米。
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
     * Apothem of the front aperture's image on the reticle plane as the eye sees it at full aim (similar triangles
     * from the eye: about 2.69 model units). Anything drawn within this distance of the axis is inside the bore.
     * 完全瞄准时自眼点看前孔在分划平面上的像的内切半径（由眼点出发的相似三角形：约 2.69 模型单位）。距轴线不超过该值的内容
     * 都位于内孔之内。
     */
    public static final float RETICLE_WINDOW_APOTHEM = WINDOW_APOTHEM * RETICLE_DISTANCE / (EYE.z() - WINDOW_FRONT_Z);

    // ---- Reticle layout on its plane, model units from the axis / 分划在其平面上的布局（距轴线的模型单位）----
    /** Bar thickness of the arms, the stem and the ticks. / 短臂、主干与刻度线的线宽。 */
    public static final float RETICLE_STROKE = 0.09F;
    /** Edge of the centre dot, the aim point of a direct shot. / 中心点边长，即直射的瞄准点。 */
    public static final float RETICLE_DOT = 0.13F;
    /** The left, right and top arms run from here to {@link #RETICLE_ARM_END}. / 左、右、上三条短臂的起点。 */
    public static final float RETICLE_ARM_START = 0.22F;
    public static final float RETICLE_ARM_END = 0.55F;
    /** Each range tick runs this far left of the axis. / 每条刻度线向轴线左侧延伸的长度。 */
    public static final float SCALE_TICK_LENGTH = 0.30F;
    /** Axis to the left edge of a range label. / 轴线到射程数字左缘的距离。 */
    public static final float SCALE_LABEL_GAP = 0.12F;
    /** Height of a label cell, centred on its tick (the digits fill 14 of its 16 texel rows). / 数字单元格的高度。 */
    public static final float SCALE_LABEL_HEIGHT = 0.30F;
    /** Air kept between a mark and the bore's wall. / 刻度与内孔壁之间保留的空隙。 */
    public static final float SCALE_WINDOW_MARGIN = 0.04F;

    // ---- Reticle atlas m2a1_reticle.png (128 x 128, colour baked) / 分划图集 ----
    public static final int ATLAS_SIZE = 128;
    /** A solid red patch; bars sample its middle so linear filtering never reaches its edge. / 纯红色块。 */
    public static final int ATLAS_SOLID_X = 6;
    public static final int ATLAS_SOLID_Y = 6;
    public static final int ATLAS_SOLID_SIZE = 8;
    /**
     * Range labels: label {@code i} (the {@code i}-th mark, "25" ... "200") sits left-aligned in the cell at
     * ({@link #ATLAS_LABEL_X} + (i mod 2) x {@link #ATLAS_LABEL_STEP_X}, {@link #ATLAS_LABEL_Y} + (i / 2) x
     * {@link #ATLAS_LABEL_STEP_Y}), {@link #ATLAS_LABEL_WIDTH} x {@link #ATLAS_LABEL_HEIGHT} texels. Digits are 10 x 14
     * texels, 2 apart, one texel inside the cell, so a label of n digits is 12 n texels wide including that inset.
     * 射程数字：第 {@code i} 个数字（即第 {@code i} 条刻度，"25" ... "200"）左对齐放在
     * （{@link #ATLAS_LABEL_X} + (i mod 2) x {@link #ATLAS_LABEL_STEP_X}，{@link #ATLAS_LABEL_Y} + (i / 2) x
     * {@link #ATLAS_LABEL_STEP_Y}）处的单元格内，单元格为 {@link #ATLAS_LABEL_WIDTH} x {@link #ATLAS_LABEL_HEIGHT} 像素。
     * 每个数字 10 x 14 像素、间隔 2、距单元格边缘 1 像素，因此 n 位数字连同该内缩共宽 12 n 像素。
     */
    public static final int ATLAS_LABEL_X = 4;
    public static final int ATLAS_LABEL_Y = 24;
    public static final int ATLAS_LABEL_STEP_X = 62;
    public static final int ATLAS_LABEL_STEP_Y = 24;
    public static final int ATLAS_LABEL_WIDTH = 36;
    public static final int ATLAS_LABEL_HEIGHT = 16;
    public static final int ATLAS_DIGIT_ADVANCE = 12;

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
    private static final float MIN_W = 1.0E-4F;
    /** The front aperture's corners, vertex space (read only). / 前孔各角（顶点空间，只读）。 */
    private static final Vector3fc[] FRONT_WINDOW = buildFrontWindow();
    /** The centre mark: dot, left, right and top arm, vertex space (read only). / 中心分划：点与左、右、上短臂。 */
    private static final Rect[] CENTRE_MARK = buildCentreMark();
    private static final Rect SOLID_UV = buildSolidUv();

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
     * Full aim pose of the launcher, vertex space to base space: {@code S T(-E)}, camera-aligned with the eye point at
     * the origin, scale {@link #AIM_SCALE}. Fresh copy.
     * 发射器的完全瞄准姿态（顶点空间到基准空间）：{@code S T(-E)}，与相机对齐、眼点位于原点，缩放为 {@link #AIM_SCALE}。
     * 返回新副本。
     */
    public static Matrix4f aimPose() {
        Vector3f eye = anchor();
        return new Matrix4f().scaling(AIM_SCALE).translate(-eye.x, -eye.y, -eye.z);
    }

    /** The eye point in vertex space, the blend's anchor. / 顶点空间中的眼点，即过渡锚点。 */
    public static Vector3f anchor() {
        return new Vector3f(EYE).div(UNITS_PER_BLOCK);
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
     * An axis-aligned rectangle, {@code x0 < x1}, {@code y0 < y1}: on the reticle plane {@code z = RETICLE_Z / 16} in
     * vertex space (+x the launcher's right, +y up), or a texture region in UV (u along +x, v from the top (y1) down,
     * so {@code y0} is the top row's v).
     * 轴对齐矩形，{@code x0 < x1}、{@code y0 < y1}：位于顶点空间的分划平面 {@code z = RETICLE_Z / 16} 上（+x 为发射器右侧，
     * +y 向上），或为 UV 中的贴图区域（u 沿 +x，v 自上（y1）向下，因此 {@code y0} 是顶行的 v）。
     */
    public record Rect(float x0, float y0, float x1, float y1) {
    }

    /**
     * The centre mark in vertex space: the dot on the axis, then the left, right and top arms. The fourth, lower arm
     * is the scale's stem ({@link #stemRect}). Fresh array of shared records.
     * 顶点空间中的中心分划：轴线上的点，以及左、右、上三条短臂。第四条（下方）短臂就是刻度尺的主干（{@link #stemRect}）。
     * 返回新数组（记录共享）。
     */
    public static Rect[] centreMark() {
        return CENTRE_MARK.clone();
    }

    /**
     * Model units below the sight axis, on the reticle plane, of a point the shooter sees {@code holdoverRadians} below
     * the bore line in the world projection: {@code RETICLE_DISTANCE tan(h) tan(handFov / 2) / tan(worldFov / 2)}, so
     * the hand-pass mark and the world direction share a screen row. NaN for unusable inputs.
     * 射手在世界投影中看到位于枪膛线下方 {@code holdoverRadians} 的点，在分划平面上位于瞄具轴线下方多少模型单位：
     * {@code RETICLE_DISTANCE tan(h) tan(手部视场角 / 2) / tan(世界视场角 / 2)}，使手部渲染中的刻度与该世界方向落在同一屏幕行。
     * 输入不可用时为 NaN。
     */
    public static double scaleOffset(double holdoverRadians, double handFovDegrees, double worldFovDegrees) {
        if (!Double.isFinite(holdoverRadians) || !(Math.abs(holdoverRadians) < Math.PI / 2.0)
                || !usableFov(handFovDegrees) || !usableFov(worldFovDegrees)) {
            return Double.NaN;
        }
        return RETICLE_DISTANCE * Math.tan(holdoverRadians) * Math.tan(Math.toRadians(handFovDegrees) / 2.0)
                / Math.tan(Math.toRadians(worldFovDegrees) / 2.0);
    }

    /**
     * Whether the tick {@code offset} units below the axis lies inside the bore with {@link #SCALE_WINDOW_MARGIN} to
     * spare, below the centre mark's arms (so it can never run into them).
     * 位于轴线下方 {@code offset} 单位的刻度线是否落在内孔之内（留有 {@link #SCALE_WINDOW_MARGIN}），且位于中心分划短臂之下
     * （因此绝不会与其相碰）。
     */
    public static boolean tickFits(double offset) {
        double half = RETICLE_STROKE / 2.0;
        return Double.isFinite(offset) && offset - half > RETICLE_ARM_START
                && insideWindow(SCALE_TICK_LENGTH, offset + half);
    }

    /**
     * Whether the label of {@code digits} digits beside the tick at {@code offset} lies inside the bore as well.
     * 位于 {@code offset} 处刻度线旁、共 {@code digits} 位的数字是否同样落在内孔之内。
     */
    public static boolean labelFits(double offset, int digits) {
        return tickFits(offset) && digits > 0
                && insideWindow(SCALE_LABEL_GAP + labelWidth(digits), offset + SCALE_LABEL_HEIGHT / 2.0);
    }

    /**
     * Whether a mark at {@code offset} keeps clear of the mark drawn above it at {@code previousOffset} (NaN = none
     * yet): their label cells must not touch, so a steep look, where the marks crowd together, thins the scale out
     * instead of stacking numbers on each other.
     * {@code offset} 处的刻度是否与其上方已绘制的 {@code previousOffset} 处刻度（NaN 表示尚无）保持间距：两者的数字单元格不得相碰，
     * 因此在俯仰很大、刻度挤在一起时，刻度尺会变稀疏，而不是把数字叠在一起。
     */
    public static boolean markClear(double offset, double previousOffset) {
        return Double.isNaN(previousOffset) || offset - previousOffset >= SCALE_LABEL_HEIGHT + RETICLE_STROKE;
    }

    /** Width on the plane of a label's drawn part, {@code digits} digits. / {@code digits} 位数字实际绘制部分的宽度。 */
    public static float labelWidth(int digits) {
        return SCALE_LABEL_HEIGHT * Math.max(0, digits) * ATLAS_DIGIT_ADVANCE / ATLAS_LABEL_HEIGHT;
    }

    /**
     * The tick {@code offset} model units below the axis, vertex space: from {@link #SCALE_TICK_LENGTH} left of the
     * axis to the stem's left edge (they abut, never overlap, so the translucent layer blends each texel once).
     * 轴线下方 {@code offset} 处的刻度线（顶点空间）：自轴线左侧 {@link #SCALE_TICK_LENGTH} 到主干左缘（两者相接而不重叠，
     * 半透明渲染层对每个像素只混合一次）。
     */
    public static Rect tickRect(double offset) {
        float y = EYE.y() - (float) offset;
        float half = RETICLE_STROKE / 2.0F;
        return planeRect(EYE.x() - SCALE_TICK_LENGTH, y - half, EYE.x() - half, y + half);
    }

    /**
     * The label quad beside the tick at {@code offset}, vertex space: {@code digits} digits wide, one cell high,
     * centred on the tick's row. Its texture region is {@link #labelUv} of the same mark and digit count.
     * {@code offset} 处刻度线旁的数字四边形（顶点空间）：宽 {@code digits} 位、高一个单元格，以刻度线所在行为中心。其贴图区域为
     * 同一刻度、同一位数的 {@link #labelUv}。
     */
    public static Rect labelRect(double offset, int digits) {
        float y = EYE.y() - (float) offset;
        float half = SCALE_LABEL_HEIGHT / 2.0F;
        float x = EYE.x() + SCALE_LABEL_GAP;
        return planeRect(x, y - half, x + labelWidth(digits), y + half);
    }

    /**
     * The stem under the centre mark, vertex space: from {@link #RETICLE_ARM_START} below the axis down to
     * {@code lowestOffset} (the lowest tick drawn, its lower edge included), and never shorter than the other three
     * arms. / 中心分划下方的主干（顶点空间）：自轴线下方 {@link #RETICLE_ARM_START} 向下到 {@code lowestOffset}（所绘制的最低
     * 刻度线，含其下缘），且不短于另外三条短臂。
     */
    public static Rect stemRect(double lowestOffset) {
        float half = RETICLE_STROKE / 2.0F;
        float end = Double.isFinite(lowestOffset) ? Math.max(RETICLE_ARM_END, (float) lowestOffset + half)
                : RETICLE_ARM_END;
        return planeRect(EYE.x() - half, EYE.y() - end, EYE.x() + half, EYE.y() - RETICLE_ARM_START);
    }

    /** UV of the solid patch's middle, for every bar. / 纯色块中部的 UV，用于所有线条。 */
    public static Rect solidUv() {
        return SOLID_UV;
    }

    /**
     * UV of the first {@code digits} digits of label {@code index} (0 = the first scale mark). Out-of-range arguments
     * are clamped. / 第 {@code index} 个数字（0 为第一条刻度）前 {@code digits} 位的 UV。越界参数会被钳制。
     */
    public static Rect labelUv(int index, int digits) {
        int i = Math.max(0, index);
        float u0 = ATLAS_LABEL_X + (i % 2) * ATLAS_LABEL_STEP_X;
        float v0 = ATLAS_LABEL_Y + (i / 2) * ATLAS_LABEL_STEP_Y;
        float width = Math.min(ATLAS_LABEL_WIDTH, Math.max(0, digits) * ATLAS_DIGIT_ADVANCE);
        return new Rect(u0 / ATLAS_SIZE, v0 / ATLAS_SIZE, (u0 + width) / ATLAS_SIZE,
                (v0 + ATLAS_LABEL_HEIGHT) / ATLAS_SIZE);
    }

    /** Whether the point (x right of, y below the axis) keeps the margin to the bore's wall. / 点是否位于内孔内并留有空隙。 */
    private static boolean insideWindow(double x, double y) {
        return Math.hypot(x, y) <= RETICLE_WINDOW_APOTHEM - SCALE_WINDOW_MARGIN;
    }

    /** Model units to a vertex-space {@link Rect}. / 模型单位转为顶点空间的 {@link Rect}。 */
    private static Rect planeRect(float x0, float y0, float x1, float y1) {
        return new Rect(x0 / UNITS_PER_BLOCK, y0 / UNITS_PER_BLOCK, x1 / UNITS_PER_BLOCK, y1 / UNITS_PER_BLOCK);
    }

    private static Rect[] buildCentreMark() {
        float x = EYE.x();
        float y = EYE.y();
        float dot = RETICLE_DOT / 2.0F;
        float half = RETICLE_STROKE / 2.0F;
        return new Rect[]{
                planeRect(x - dot, y - dot, x + dot, y + dot),
                planeRect(x - RETICLE_ARM_END, y - half, x - RETICLE_ARM_START, y + half),
                planeRect(x + RETICLE_ARM_START, y - half, x + RETICLE_ARM_END, y + half),
                planeRect(x - half, y + RETICLE_ARM_START, x + half, y + RETICLE_ARM_END),
        };
    }

    private static Rect buildSolidUv() {
        // The middle half of the patch. / 色块的中间一半。
        float inset = ATLAS_SOLID_SIZE / 4.0F;
        return new Rect((ATLAS_SOLID_X + inset) / ATLAS_SIZE, (ATLAS_SOLID_Y + inset) / ATLAS_SIZE,
                (ATLAS_SOLID_X + ATLAS_SOLID_SIZE - inset) / ATLAS_SIZE,
                (ATLAS_SOLID_Y + ATLAS_SOLID_SIZE - inset) / ATLAS_SIZE);
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

    private static boolean usableFov(double degrees) {
        return Double.isFinite(degrees) && degrees > 0.0 && degrees < 180.0;
    }

    private static float clamp01(float value) {
        return value >= 0.0F ? Math.min(1.0F, value) : 0.0F;
    }
}
