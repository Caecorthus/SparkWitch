package dev.caecorthus.sparkwitch.client.scope;

/**
 * The scope shadow (owner plan D14: fast turning or moving darkens the lens edge): a smoothed exit-pupil offset in
 * lens radii, GUI axes (+x right, +y down), pointing the way the view swings. The lens shader shifts a slightly larger
 * clear circle by this offset, so a dark crescent enters from the opposite edge. Driven by the camera's yaw/pitch rate
 * plus a smaller share of the camera's sideways and vertical motion; it rises quickly, decays when still, ignores slow
 * aiming below a dead zone, and is capped. Pure state, render thread only; one update per rendered frame.
 * 镜内阴影（所有者计划 D14：快速转动或移动时镜片边缘变暗）：经过平滑的出瞳偏移，单位为镜片半径，GUI 坐标轴（+x 向右、
 * +y 向下），指向视角摆动方向。镜片着色器按该偏移移动一个略大的清晰圆，使暗色月牙从相反一侧边缘进入。由相机偏航/俯仰
 * 角速度驱动，再加上较小权重的相机横向与竖直移动；上升快、静止时衰减、低于死区的慢速瞄准不产生阴影，且有上限。
 * 纯状态，仅渲染线程；每个渲染帧更新一次。
 */
public final class ScopeShadow {
    /** Aiming slower than this (degrees per second) casts no shadow. / 低于此角速度（度/秒）的瞄准不产生阴影。 */
    public static final double DEAD_ZONE_DEGREES_PER_SECOND = 6.0;
    /** Offset gained per degree per second beyond the dead zone. / 超出死区后每度/秒对应的偏移。 */
    public static final double OFFSET_PER_DEGREE_PER_SECOND = 1.0 / 150.0;
    public static final double MAX_OFFSET = 0.55;
    /** Camera motion counts like this many degrees per block. / 相机每移动 1 格按这么多度计。 */
    public static final double MOTION_DEGREES_PER_BLOCK = 4.0;
    /** A camera jump longer than this per frame (teleport, respawn) is ignored. / 单帧超过此距离的相机跳变被忽略。 */
    public static final double MAX_FRAME_MOTION_BLOCKS = 2.0;
    public static final double RISE_SECONDS = 0.05;
    public static final double DECAY_SECONDS = 0.18;
    /** Longest smoothing step, so a frame hitch does not jump the shadow. / 最长平滑步长，避免卡顿时阴影跳变。 */
    public static final double MAX_STEP_SECONDS = 0.1;

    private double x;
    private double y;
    private float lastYaw;
    private float lastPitch;
    private boolean primed;

    /** Back to no shadow; the next update only records the view. / 回到无阴影；下一次更新只记录视角。 */
    public void reset() {
        x = 0.0;
        y = 0.0;
        primed = false;
    }

    /**
     * @param yawDegrees    camera yaw this frame (increases turning right) / 本帧相机偏航角（向右转时增大）
     * @param pitchDegrees  camera pitch this frame (increases looking down) / 本帧相机俯仰角（向下看时增大）
     * @param rightBlocks   camera motion since the last frame along the view's right axis / 自上一帧以来沿视角右方向的相机位移
     * @param upBlocks      camera motion since the last frame along world up / 自上一帧以来沿世界向上方向的相机位移
     * @param frameSeconds  real time since the last frame / 自上一帧以来的真实时间
     */
    public void update(float yawDegrees, float pitchDegrees, double rightBlocks, double upBlocks, double frameSeconds) {
        if (!primed || !(frameSeconds > 0.0) || !Float.isFinite(yawDegrees) || !Float.isFinite(pitchDegrees)) {
            lastYaw = yawDegrees;
            lastPitch = pitchDegrees;
            primed = Float.isFinite(yawDegrees) && Float.isFinite(pitchDegrees);
            return;
        }
        double yawRate = wrapDegrees(yawDegrees - lastYaw) / frameSeconds;
        double pitchRate = (pitchDegrees - lastPitch) / frameSeconds;
        lastYaw = yawDegrees;
        lastPitch = pitchDegrees;
        double motion = Math.sqrt(rightBlocks * rightBlocks + upBlocks * upBlocks);
        double motionScale = Double.isFinite(motion) && motion <= MAX_FRAME_MOTION_BLOCKS
                ? MOTION_DEGREES_PER_BLOCK / frameSeconds
                : 0.0;
        // Moving right reads like swinging right; moving up reads like pitching up (GUI -y).
        // 向右移动视同向右摆动；向上移动视同向上抬（GUI -y）。
        double rateX = yawRate + rightBlocks * motionScale;
        double rateY = pitchRate - upBlocks * motionScale;
        double rate = Math.sqrt(rateX * rateX + rateY * rateY);
        double targetX = 0.0;
        double targetY = 0.0;
        if (Double.isFinite(rate) && rate > DEAD_ZONE_DEGREES_PER_SECOND) {
            double offset = Math.min(MAX_OFFSET, (rate - DEAD_ZONE_DEGREES_PER_SECOND) * OFFSET_PER_DEGREE_PER_SECOND);
            targetX = rateX / rate * offset;
            targetY = rateY / rate * offset;
        }
        boolean rising = targetX * targetX + targetY * targetY >= x * x + y * y;
        double step = Math.min(frameSeconds, MAX_STEP_SECONDS);
        double follow = 1.0 - Math.exp(-step / (rising ? RISE_SECONDS : DECAY_SECONDS));
        x += (targetX - x) * follow;
        y += (targetY - y) * follow;
    }

    public float x() {
        return (float) x;
    }

    public float y() {
        return (float) y;
    }

    static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped >= 180.0) {
            wrapped -= 360.0;
        } else if (wrapped < -180.0) {
            wrapped += 360.0;
        }
        return wrapped;
    }
}
