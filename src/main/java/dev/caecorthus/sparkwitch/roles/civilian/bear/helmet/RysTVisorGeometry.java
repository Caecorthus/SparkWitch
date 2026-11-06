package dev.caecorthus.sparkwitch.roles.civilian.bear.helmet;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Stable contract (WP2, for the WP3 ricochet): where a shot crosses the lowered Rys-T visor glass, as the
 * {@link VisorCrack} glass coordinates (u, v in [0, 1], u = 0 at the wearer's LEFT, v = 0 at the TOP). Pure and
 * side-safe. The glass is the {@code glass} element of {@code models/item/rys_t.json} in head-model pixels
 * ({@code display.head} scale 1.6: one unit is one skin pixel and the wearer's head is the box [4, 12]^3, -z is the
 * face, +x the wearer's right); the eye sits at the head centre on the eye line, where the camera is. A shot is taken
 * to be aimed through the eye: it crosses the glass plane at the eye-to-shooter direction scaled to the glass depth.
 * Only the DOWN pose has a glass in the line of fire (D4: cracks only with the visor down). The art package must keep
 * the glass element on these numbers ({@code RysTVisorGeometryTest} reads both models).
 * 稳定契约（WP2，供 WP3 跳弹使用）：射击穿过放下的 Rys-T 面罩玻璃的位置，以 {@link VisorCrack} 玻璃坐标表示（u、v 在
 * [0, 1] 内，u = 0 为佩戴者左侧，v = 0 为顶部）。纯函数，两端通用。玻璃即 {@code models/item/rys_t.json} 中的 {@code glass}
 * 元素，单位为头部模型像素（{@code display.head} 缩放 1.6：一个单位即一个皮肤像素，佩戴者头部为 [4, 12]^3 立方体，-z 为
 * 面部，+x 为佩戴者右侧）；眼睛位于头部中心的视线高度，即相机所在处。射击视为瞄准眼睛：它在眼睛指向射手的方向上、按玻璃
 * 深度缩放后与玻璃平面相交。只有放下姿态的玻璃处于射线上（D4：只有面罩放下时才会产生裂纹）。美术包必须让玻璃元素保持这些
 * 数值（{@code RysTVisorGeometryTest} 会读取两个模型）。
 */
public final class RysTVisorGeometry {
    /**
     * Crack decal styles in the art atlases ({@code VisorCrack#style} wraps modulo this). / 美术图集中的裂纹贴花样式数
     * （{@code VisorCrack#style} 按此取模回绕）。
     */
    public static final int CRACK_STYLE_COUNT = 6;

    /**
     * Visor-down glass, head-model pixels: the 8 x 2 slit x 4..12, y 6.5..8.5 (Astra art, 2026-11-06). / 面罩放下时的
     * 玻璃（头部模型像素）：8 × 2 的观察缝 x 4..12、y 6.5..8.5（Astra 美术，2026-11-06）。
     */
    public static final float GLASS_MIN_X = 4.0F;
    public static final float GLASS_MAX_X = 12.0F;
    public static final float GLASS_MIN_Y = 6.5F;
    public static final float GLASS_MAX_Y = 8.5F;
    /** The glass's outer (north) face. / 玻璃外侧（北面）。 */
    public static final float GLASS_FACE_Z = 1.5F;
    /** The eye (camera) in head-model pixels: head centre, on the eye line. / 眼睛（相机）位置：头部中心、视线高度。 */
    public static final float EYE_X = 8.0F;
    public static final float EYE_Y = 7.5F;
    public static final float EYE_Z = 8.0F;

    /**
     * Visor-up pose of the same glass ({@code models/item/rys_t_visor_up.json}): the whole visor assembly turns rigidly,
     * with no lift, {@link #UP_TILT_DEGREES} about +x (right-handed, the vanilla element-rotation sense) around the
     * temple pivot ({@link #UP_PIVOT_X}, {@link #UP_PIVOT_Y}, {@link #UP_PIVOT_Z}):
     * {@code up = P + Rx(UP_TILT_DEGREES) * (down - P)}. The model bakes it as an exact 90 degree axis permutation plus
     * a legal -22.5 degree element rotation about the same pivot. Drawing only.
     * 同一块玻璃的抬起姿态（{@code models/item/rys_t_visor_up.json}）：整个面罩组件不上移，绕太阳穴枢轴
     * （{@link #UP_PIVOT_X}, {@link #UP_PIVOT_Y}, {@link #UP_PIVOT_Z}）绕 +x 轴（右手定则，即原版元素旋转方向）刚性旋转
     * {@link #UP_TILT_DEGREES}：{@code up = P + Rx(UP_TILT_DEGREES) * (down - P)}。模型中以精确的 90 度坐标轴置换加上合法的
     * -22.5 度元素旋转（同一枢轴）实现。仅用于绘制。
     */
    public static final float UP_TILT_DEGREES = 67.5F;
    public static final float UP_PIVOT_X = 8.0F;
    public static final float UP_PIVOT_Y = 9.5F;
    public static final float UP_PIVOT_Z = 7.25F;

    private static final double EPSILON = 1.0E-6;

    private RysTVisorGeometry() {
    }

    /** A point on the visor glass. / 面罩玻璃上的一点。 */
    public record GlassPoint(float u, float v) {
    }

    /**
     * Where a shot travelling along {@code incomingDir} (shooter towards wearer) crosses the visor-down glass of a
     * wearer whose head looks along {@code headYaw}/{@code pitch} (degrees, Minecraft convention), or null when it
     * comes from the side or behind or misses the glass (then it struck the titanium plate or shell).
     * 沿 {@code incomingDir}（射手指向佩戴者）飞来的射击穿过面罩放下玻璃的位置，佩戴者头部朝向为 {@code headYaw}/{@code pitch}
     * （角度，Minecraft 约定）；来自侧面或后方、或未命中玻璃（打在钛合金面板或外壳上）时为 null。
     */
    public static @Nullable GlassPoint glassPoint(@Nullable Vec3d incomingDir, float headYaw, float pitch) {
        return incomingDir == null ? null : project(incomingDir.x, incomingDir.y, incomingDir.z, headYaw, pitch, false);
    }

    /**
     * Like {@link #glassPoint} but clamped onto the glass edge for any shot from the front half-space; null only when
     * the shot does not come from in front of the face. / 与 {@link #glassPoint} 相同，但来自面部前方半空间的射击都会被夹到
     * 玻璃边缘；只有射击不来自面部前方时才为 null。
     */
    public static @Nullable GlassPoint nearestGlassPoint(@Nullable Vec3d incomingDir, float headYaw, float pitch) {
        return incomingDir == null ? null : project(incomingDir.x, incomingDir.y, incomingDir.z, headYaw, pitch, true);
    }

    /**
     * A crack at {@code point} with a random decal angle and style (server random). / 在 {@code point} 处生成裂纹，贴花
     * 角度与样式随机（服务端随机源）。
     */
    public static VisorCrack crackAt(GlassPoint point, Random random) {
        return new VisorCrack(point.u(), point.v(), random.nextFloat() * MathHelper.TAU,
                random.nextInt(CRACK_STYLE_COUNT));
    }

    static @Nullable GlassPoint project(double dx, double dy, double dz, float headYaw, float pitch, boolean clamp) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(length > EPSILON) || !Double.isFinite(length)) {
            return null;
        }
        // Towards the shooter. / 指向射手。
        double sx = -dx / length;
        double sy = -dy / length;
        double sz = -dz / length;
        double yaw = Math.toRadians(headYaw);
        double pitchRad = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRad);
        // Look (forward), right and up of the head (Entity#getRotationVector; up = right x forward).
        // 头部的前方、右方与上方（Entity#getRotationVector；上方 = 右方 × 前方）。
        double fx = -Math.sin(yaw) * cosPitch;
        double fy = -Math.sin(pitchRad);
        double fz = Math.cos(yaw) * cosPitch;
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);
        double ux = -rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy;
        double forward = sx * fx + sy * fy + sz * fz;
        if (!(forward > EPSILON)) {
            return null;
        }
        double depth = EYE_Z - GLASS_FACE_Z;
        double x = (sx * rx + sz * rz) / forward * depth;
        double y = (sx * ux + sy * uy + sz * uz) / forward * depth;
        double minX = GLASS_MIN_X - EYE_X;
        double maxX = GLASS_MAX_X - EYE_X;
        double minY = GLASS_MIN_Y - EYE_Y;
        double maxY = GLASS_MAX_Y - EYE_Y;
        if (clamp) {
            x = MathHelper.clamp(x, minX, maxX);
            y = MathHelper.clamp(y, minY, maxY);
        } else if (x < minX || x > maxX || y < minY || y > maxY) {
            return null;
        }
        return new GlassPoint((float) ((x - minX) / (maxX - minX)), (float) ((maxY - y) / (maxY - minY)));
    }
}
