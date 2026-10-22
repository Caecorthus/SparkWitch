package dev.caecorthus.sparkwitch.client.blackraven.crow;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Pure math of the Raven Form boundary indicator (owner Q10: screen-edge glow + arrow + distance). The clamp box is the
 * same one the server enforces ({@code BlackRavenCrowFlightRules.bounds}: Wathe's play area, x/z intersected with the
 * world border, y between minY and maxY). A face warns when it is useful: each of the four horizontal faces within
 * {@link #WARN_DISTANCE} of the crow's hull, and the floor or ceiling only within {@link #VERTICAL_WARN_DISTANCE} and only
 * while the crow moves toward it (the play area's floor is the fall line under the train, usually within 8 blocks of a
 * crow flying inside a carriage, so a plain 8-block floor warning would glow all the time). A warning face glows the
 * screen edge(s) it lies toward, brighter as the crow approaches; an arrow points at the nearest warning face with its
 * distance. Motion is the pilot's commanded velocity in blocks per tick. Camera space is derived from yaw/pitch in
 * Minecraft's convention (yaw 0 faces +Z).
 * 化鸦边界提示的纯计算（所有者 Q10：屏幕边缘泛光 + 箭头 + 距离）。限制框与服务端执行的完全相同（BlackRavenCrowFlightRules.bounds：
 * Wathe 游戏区域，x/z 与世界边界取交集，y 在 minY 与 maxY 之间）。边界面只在有用时警告：四个水平面在距离乌鸦机身 WARN_DISTANCE
 * 以内时警告；底面与顶面只在 VERTICAL_WARN_DISTANCE 以内、且乌鸦正朝它移动时警告（游戏区域的底面是列车下方的坠落线，在车厢内
 * 飞行时通常就在 8 格以内，若同样按 8 格警告，底边会一直泛光）。警告中的边界面让其所在方向的屏幕边缘泛光，越近越亮；箭头指向最近的
 * 警告边界面并显示距离。运动取驾驶端的指令速度（格/刻）。镜头空间按 Minecraft 约定由 yaw/pitch 求得（yaw 0 朝向 +Z）。
 */
public final class BlackRavenCrowBoundaryRules {
    /** Horizontal faces closer than this start to glow. / 近于此距离的水平边界面开始泛光。 */
    public static final double WARN_DISTANCE = 8.0;
    /**
     * Floor and ceiling closer than this start to glow, only while the crow moves toward them.
     * 底面与顶面近于此距离、且乌鸦正朝其移动时才开始泛光。
     */
    public static final double VERTICAL_WARN_DISTANCE = 2.0;
    /**
     * Least speed (blocks per tick) along a face's outward normal that counts as moving toward it.
     * 沿边界面外法线方向、视为正朝其移动的最小速度（格/刻）。
     */
    public static final double APPROACH_SPEED = 1.0E-3;
    /**
     * A face mostly ahead (its normal's forward share above this) is shown as an "ahead" marker above the crosshair
     * instead of an edge arrow, and lights every edge a little.
     * 主要位于正前方的边界面（法线前向分量高于此值）改为在准星上方显示"前方"标记，而不是边缘箭头，并让四边都微亮。
     */
    public static final double AHEAD_DOT = 0.85;
    /** Share of a face's intensity that a face ahead adds to every edge. / 前方边界面分给四边的强度比例。 */
    public static final float AHEAD_EDGE_SHARE = 0.45F;
    private static final double MIN_DIRECTION = 1.0E-3;

    private BlackRavenCrowBoundaryRules() {
    }

    /** A face of the clamp box and its outward world normal. / 限制框的一个面及其向外的世界法线。 */
    public enum Face {
        WEST(-1, 0, 0),
        EAST(1, 0, 0),
        DOWN(0, -1, 0),
        UP(0, 1, 0),
        NORTH(0, 0, -1),
        SOUTH(0, 0, 1);

        private final Vec3d normal;

        Face(int x, int y, int z) {
            this.normal = new Vec3d(x, y, z);
        }

        public Vec3d normal() {
            return normal;
        }

        /** Floor or ceiling. / 底面或顶面。 */
        public boolean vertical() {
            return normal.y != 0.0;
        }
    }

    /** Gap between the hull and one face (never negative; infinite for an unbounded axis). / 机身与某个面的间距。 */
    public static double distance(Face face, Box hull, Box bounds) {
        double gap = switch (face) {
            case WEST -> hull.minX - bounds.minX;
            case EAST -> bounds.maxX - hull.maxX;
            case DOWN -> hull.minY - bounds.minY;
            case UP -> bounds.maxY - hull.maxY;
            case NORTH -> hull.minZ - bounds.minZ;
            case SOUTH -> bounds.maxZ - hull.maxZ;
        };
        if (Double.isNaN(gap)) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0.0, gap);
    }

    /** The face's warning distance. / 该边界面的警告距离。 */
    public static double warnDistance(Face face) {
        return face.vertical() ? VERTICAL_WARN_DISTANCE : WARN_DISTANCE;
    }

    /** {@code motion} heads toward the face. / {@code motion} 正朝该边界面移动。 */
    public static boolean approaches(Face face, Vec3d motion) {
        return motion.dotProduct(face.normal()) > APPROACH_SPEED;
    }

    /**
     * Whether the face warns: a horizontal face within {@link #WARN_DISTANCE}; the floor or ceiling within
     * {@link #VERTICAL_WARN_DISTANCE} and only while approaching it.
     * 该边界面是否警告：水平面在 WARN_DISTANCE 以内；底面或顶面在 VERTICAL_WARN_DISTANCE 以内且正朝其移动。
     */
    public static boolean warns(Face face, double distance, Vec3d motion) {
        return distance < warnDistance(face) && (!face.vertical() || approaches(face, motion));
    }

    /** Glow strength of one face, 0 when it does not warn. / 单个边界面的泛光强度，不警告时为 0。 */
    public static float intensity(Face face, double distance, Vec3d motion) {
        return warns(face, distance, motion) ? ease(distance, warnDistance(face)) : 0.0F;
    }

    /** 0 at {@code warnDistance} or beyond, 1 at the face, eased (smoothstep). / 警告距离处为 0，贴边为 1，平滑过渡。 */
    public static float ease(double distance, double warnDistance) {
        if (!(distance < warnDistance)) {
            return 0.0F;
        }
        double t = 1.0 - Math.max(0.0, distance) / warnDistance;
        return (float) (t * t * (3.0 - 2.0 * t));
    }

    public record FaceDistance(Face face, double distance) {
    }

    /** The nearest warning face, or null. / 最近的警告边界面，没有时为 null。 */
    public static @Nullable FaceDistance nearest(Box hull, Box bounds, Vec3d motion) {
        FaceDistance best = null;
        for (Face face : Face.values()) {
            double distance = distance(face, hull, bounds);
            if (warns(face, distance, motion) && (best == null || distance < best.distance())) {
                best = new FaceDistance(face, distance);
            }
        }
        return best;
    }

    /** Camera axes in world space. / 世界空间中的镜头坐标轴。 */
    public record Basis(Vec3d right, Vec3d up, Vec3d forward) {
        /** (right, up, forward) components of a world vector. / 世界向量在 (右, 上, 前) 上的分量。 */
        public Vec3d toCamera(Vec3d world) {
            return new Vec3d(world.dotProduct(right), world.dotProduct(up), world.dotProduct(forward));
        }
    }

    /**
     * Camera basis for a look (degrees; positive pitch looks down). Yaw 0 faces +Z with +X on the left.
     * 视线对应的镜头坐标系（角度制；俯仰为正表示向下看）。yaw 0 朝向 +Z，+X 在左侧。
     */
    public static Basis basis(float yawDegrees, float pitchDegrees) {
        double yaw = yawDegrees * MathHelper.RADIANS_PER_DEGREE;
        double pitch = pitchDegrees * MathHelper.RADIANS_PER_DEGREE;
        Vec3d forward = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3d right = new Vec3d(-Math.cos(yaw), 0.0, -Math.sin(yaw));
        Vec3d up = right.crossProduct(forward);
        return new Basis(right, up, forward);
    }

    /** Glow strength 0..1 of each screen edge. / 各屏幕边缘的泛光强度 0..1。 */
    public record EdgeGlow(float left, float right, float top, float bottom) {
        public static final EdgeGlow NONE = new EdgeGlow(0.0F, 0.0F, 0.0F, 0.0F);

        public boolean any() {
            return left > 0.0F || right > 0.0F || top > 0.0F || bottom > 0.0F;
        }
    }

    /**
     * Every warning face lights the edges it lies toward (its normal's camera-space right/up components, weighted by its
     * intensity; the strongest face wins per edge). A face ahead also lights every edge a little; a face straight behind
     * relies on the arrow.
     * 每个警告中的边界面点亮其所在方向的屏幕边缘（按法线在镜头空间的右/上分量乘以强度；每条边取最强者）。
     * 前方的边界面还会让四边微亮；正后方的边界面由箭头提示。
     */
    public static EdgeGlow edgeGlow(Box hull, Box bounds, Basis basis, Vec3d motion) {
        float left = 0.0F;
        float right = 0.0F;
        float top = 0.0F;
        float bottom = 0.0F;
        for (Face face : Face.values()) {
            float k = intensity(face, distance(face, hull, bounds), motion);
            if (k <= 0.0F) {
                continue;
            }
            Vec3d local = basis.toCamera(face.normal());
            left = Math.max(left, k * positive(-local.x));
            right = Math.max(right, k * positive(local.x));
            top = Math.max(top, k * positive(local.y));
            bottom = Math.max(bottom, k * positive(-local.y));
            float ahead = k * AHEAD_EDGE_SHARE * positive(local.z);
            left = Math.max(left, ahead);
            right = Math.max(right, ahead);
            top = Math.max(top, ahead);
            bottom = Math.max(bottom, ahead);
        }
        return new EdgeGlow(clamp01(left), clamp01(right), clamp01(top), clamp01(bottom));
    }

    /**
     * Where the arrow sits and where it points (screen pixels, y down; angle in radians, 0 = right). A face ahead puts
     * the marker above the crosshair pointing up; otherwise the arrow sits {@code inset} inside the screen edge where a
     * ray from the centre along the face's projected direction leaves the screen (a face straight behind points down).
     * 箭头的位置与指向（屏幕像素，y 向下；角度为弧度，0 指向右）。前方的边界面把标记放在准星上方并指向上；否则箭头位于从屏幕中心
     * 沿边界面投影方向射出、离开屏幕处向内 inset 的位置（正后方的边界面指向下）。
     */
    public record Arrow(double x, double y, double angle, boolean ahead) {
    }

    public static Arrow arrow(Vec3d normal, Basis basis, double width, double height, double inset) {
        Vec3d local = basis.toCamera(normal);
        double halfW = width * 0.5;
        double halfH = height * 0.5;
        if (local.z > AHEAD_DOT) {
            return new Arrow(halfW, halfH - Math.min(halfH * 0.5, 36.0), -Math.PI * 0.5, true);
        }
        double dirX = local.x;
        double dirY = -local.y;
        double length = Math.hypot(dirX, dirY);
        if (length < MIN_DIRECTION) {
            dirX = 0.0;
            dirY = 1.0;
            length = 1.0;
        }
        double ux = dirX / length;
        double uy = dirY / length;
        double tx = Math.abs(ux) < MIN_DIRECTION ? Double.POSITIVE_INFINITY : halfW / Math.abs(ux);
        double ty = Math.abs(uy) < MIN_DIRECTION ? Double.POSITIVE_INFINITY : halfH / Math.abs(uy);
        double t = Math.max(0.0, Math.min(tx, ty) - inset);
        return new Arrow(halfW + ux * t, halfH + uy * t, Math.atan2(uy, ux), false);
    }

    /** One decimal, locale-independent. / 一位小数，与语言环境无关。 */
    public static String formatDistance(double distance) {
        return String.format(Locale.ROOT, "%.1f", Math.max(0.0, distance));
    }

    /** A component pointing that way; float noise counts as none. / 指向该方向的分量；浮点噪声视为无。 */
    private static float positive(double component) {
        return component > MIN_DIRECTION ? (float) component : 0.0F;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
