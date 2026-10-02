package dev.caecorthus.sparkwitch.client.blind.gate;

/**
 * Pure bump classification and throttle for the local Blind (C10: client only, no packet, no public sound). A wall
 * bump is the rising edge of a hard horizontal collision (a glancing {@code collidedSoftly} slide never counts) while a
 * movement key is held; a head bump is a vertical collision while airborne. At most one bump per
 * {@link #THROTTLE_TICKS}; a backwards clock (world change) never blocks the next bump.
 * 本地盲人的纯碰撞判定与节流（C10：仅客户端，无数据包，无公开声音）。撞墙指按住移动键时“硬”水平碰撞的上升沿
 * （擦墙滑行 {@code collidedSoftly} 不算）；撞头指离地时的竖直碰撞。每 {@link #THROTTLE_TICKS} 刻至多一次；
 * 时钟倒退（换世界）不会阻挡下一次碰撞。
 */
public final class BlindBumpDetector {
    public static final int THROTTLE_TICKS = 8;

    public enum Bump {
        NONE,
        WALL,
        HEAD
    }

    private boolean wasHardHorizontal;
    private boolean hasBumped;
    private long lastBumpTick;

    /** Feeds one client tick of the local player's collision flags. / 输入本地玩家一个客户端刻的碰撞标记。 */
    public Bump tick(long worldTick, boolean horizontalCollision, boolean collidedSoftly, boolean movementInput,
                     boolean verticalCollision, boolean onGround) {
        boolean hardHorizontal = horizontalCollision && !collidedSoftly;
        boolean risingHorizontal = hardHorizontal && !wasHardHorizontal;
        wasHardHorizontal = hardHorizontal;
        Bump bump = classify(risingHorizontal, movementInput, verticalCollision, onGround);
        if (bump == Bump.NONE || throttled(worldTick)) {
            return Bump.NONE;
        }
        hasBumped = true;
        lastBumpTick = worldTick;
        return bump;
    }

    public static Bump classify(boolean risingHardHorizontal, boolean movementInput, boolean verticalCollision,
                                boolean onGround) {
        if (risingHardHorizontal && movementInput) {
            return Bump.WALL;
        }
        if (verticalCollision && !onGround) {
            return Bump.HEAD;
        }
        return Bump.NONE;
    }

    public void reset() {
        wasHardHorizontal = false;
        hasBumped = false;
        lastBumpTick = 0L;
    }

    private boolean throttled(long worldTick) {
        return hasBumped && worldTick >= lastBumpTick && worldTick - lastBumpTick < THROTTLE_TICKS;
    }

    /**
     * World-space horizontal direction of the movement keys (vanilla {@code Entity.movementInputToVelocity}), unit
     * length, or {@code {0, 0}} without input.
     * 移动按键对应的世界水平方向（与原版 {@code Entity.movementInputToVelocity} 一致），单位长度；无输入时为 {@code {0, 0}}。
     */
    public static double[] inputDirection(float forward, float sideways, float yawDegrees) {
        double length = Math.sqrt(forward * forward + sideways * sideways);
        if (length < 1.0E-4) {
            return new double[]{0.0, 0.0};
        }
        double radians = Math.toRadians(yawDegrees);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double x = sideways / length;
        double z = forward / length;
        return new double[]{x * cos - z * sin, z * cos + x * sin};
    }
}
