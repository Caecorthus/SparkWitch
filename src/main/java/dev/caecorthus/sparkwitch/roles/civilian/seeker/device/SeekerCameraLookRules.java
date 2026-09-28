package dev.caecorthus.sparkwitch.roles.civilian.seeker.device;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

/**
 * Stable contract: the camera look cone shared by the owner's client view, the server clamp of
 * {@code sparkwitch:seeker_camera_look}, and the head every client renders, plus the owner client's send cadence and
 * the render smoothing. The cone is centre +-{@link SeekerRules#CAMERA_YAW_CONE} yaw (the wall normal, or the mount
 * yaw on floors and ceilings) and centre +-{@link SeekerRules#CAMERA_PITCH_CONE} pitch (level on walls, looking up
 * from floors, down from ceilings). Pure and side-neutral, so the server clamps with exactly the client's math.
 * 稳定契约：摄像头视角锥角，由拥有者客户端视角、服务端对 {@code sparkwitch:seeker_camera_look} 的钳制以及所有客户端
 * 渲染的机头共用；另含拥有者客户端的发送节奏与渲染平滑。锥角为中心 ±70° 偏航（墙面取法线，地面与天花板取安装朝向）
 * 与中心 ±45° 俯仰（墙面水平、地面朝上、天花板朝下）。纯函数且两端通用，因此服务端与客户端的钳制完全一致。
 */
public final class SeekerCameraLookRules {
    /** The owner's client sends a look at most once per this many ticks. / 拥有者客户端最多每隔此刻数发送一次视角。 */
    public static final int SEND_INTERVAL_TICKS = 2;
    /** ...and only after the look moved at least this far (degrees). / 且仅当视角变化至少此角度（度）时发送。 */
    public static final float SEND_THRESHOLD_DEGREES = 0.5F;
    /**
     * After this many ticks without a send, the client re-sends when the server's tracked look still disagrees
     * (the server drops extra packets that land in the same tick).
     * 距上次发送满此刻数后，若服务端同步回来的视角仍不一致，客户端补发一次（服务端会丢弃同一刻内多余的包）。
     */
    public static final int RESYNC_TICKS = 20;
    /** Fraction of the remaining angle the rendered head closes each client tick. / 渲染机头每个客户端刻追上剩余角度的比例。 */
    public static final float RENDER_SMOOTHING = 0.5F;
    private static final float SNAP_DEGREES = 0.01F;

    private SeekerCameraLookRules() {
    }

    /**
     * Horizontal centre of the cone: the wall normal, or the mount yaw for floor and ceiling cameras.
     * 锥角的水平中心：墙面取法线方向，地面与天花板取安装朝向。
     */
    public static float coneYawCenter(Direction facing, float mountYaw) {
        return facing.getAxis().isHorizontal() ? facing.asRotation() : MathHelper.wrapDegrees(mountYaw);
    }

    /**
     * Vertical centre of the cone: level on walls, looking up from the floor, down from the ceiling.
     * 锥角的垂直中心：墙面水平，地面朝上看，天花板朝下看。
     */
    public static float conePitchCenter(Direction facing) {
        return switch (facing) {
            case UP -> SeekerRules.CAMERA_FLOOR_PITCH_CENTER;
            case DOWN -> SeekerRules.CAMERA_CEILING_PITCH_CENTER;
            default -> 0.0F;
        };
    }

    /**
     * Clamps a yaw into centre +-70 degrees while keeping its winding, so interpolation never spins.
     * 将 yaw 钳制到中心 ±70° 内，并保留其圈数，插值时不会整圈旋转。
     */
    public static float clampConeYaw(float yaw, float center) {
        float offset = MathHelper.wrapDegrees(yaw - center);
        float clamped = MathHelper.clamp(offset, -SeekerRules.CAMERA_YAW_CONE, SeekerRules.CAMERA_YAW_CONE);
        return yaw + (clamped - offset);
    }

    /** Pitch within centre +-45 degrees and the vanilla +-90 limit. / pitch 限制在中心 ±45° 与原版 ±90° 之内。 */
    public static float clampConePitch(float pitch, float center) {
        float cone = MathHelper.clamp(pitch, center - SeekerRules.CAMERA_PITCH_CONE, center + SeekerRules.CAMERA_PITCH_CONE);
        return MathHelper.clamp(cone, -90.0F, 90.0F);
    }

    /**
     * Server clamp of an untrusted yaw for one mount: wrapped to [-180, 180) and inside the cone.
     * 服务端对某个安装位置钳制不可信的 yaw：规范到 [-180, 180) 并位于锥角内。
     */
    public static float clampYaw(Direction facing, float mountYaw, float yaw) {
        return MathHelper.wrapDegrees(clampConeYaw(MathHelper.wrapDegrees(yaw), coneYawCenter(facing, mountYaw)));
    }

    /** Server clamp of an untrusted pitch for one mount. / 服务端对某个安装位置钳制不可信的 pitch。 */
    public static float clampPitch(Direction facing, float pitch) {
        return clampConePitch(pitch, conePitchCenter(facing));
    }

    /**
     * True when two looks differ by at least {@link #SEND_THRESHOLD_DEGREES} on either axis (yaw by the short arc).
     * 两个视角在任一轴上相差至少 {@link #SEND_THRESHOLD_DEGREES} 时为 true（yaw 按最短弧计算）。
     */
    public static boolean differs(float yawA, float pitchA, float yawB, float pitchB) {
        return Math.abs(MathHelper.wrapDegrees(yawA - yawB)) >= SEND_THRESHOLD_DEGREES
                || Math.abs(pitchA - pitchB) >= SEND_THRESHOLD_DEGREES;
    }

    /**
     * Owner-client cadence: never faster than {@link #SEND_INTERVAL_TICKS}; send when the look moved since the last
     * send, or after {@link #RESYNC_TICKS} quiet ticks when the server's tracked look still disagrees.
     * 拥有者客户端节奏：不快于 {@link #SEND_INTERVAL_TICKS}；自上次发送后视角有变化时发送，或安静满
     * {@link #RESYNC_TICKS} 刻后服务端同步的视角仍不一致时补发。
     */
    public static boolean shouldSend(int ticksSinceSend, boolean movedSinceSend, boolean differsFromServer) {
        if (ticksSinceSend < SEND_INTERVAL_TICKS) {
            return false;
        }
        return movedSinceSend || (differsFromServer && ticksSinceSend >= RESYNC_TICKS);
    }

    /**
     * One client tick of render smoothing toward the synced yaw along the short arc. The result keeps the winding of
     * {@code current}, so a frame lerp between two consecutive results never spins.
     * 渲染平滑的一个客户端刻：沿最短弧逼近同步的 yaw。结果保留 {@code current} 的圈数，相邻两次结果之间的帧插值不会整圈旋转。
     */
    public static float approachYaw(float current, float target) {
        return current + approachStep(MathHelper.wrapDegrees(target - current));
    }

    /** One client tick of render smoothing toward the synced pitch. / 渲染平滑的一个客户端刻：逼近同步的 pitch。 */
    public static float approachPitch(float current, float target) {
        return current + approachStep(target - current);
    }

    private static float approachStep(float remaining) {
        return Math.abs(remaining) <= SNAP_DEGREES ? remaining : remaining * RENDER_SMOOTHING;
    }
}
