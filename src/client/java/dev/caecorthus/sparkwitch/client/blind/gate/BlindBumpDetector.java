package dev.caecorthus.sparkwitch.client.blind.gate;

import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.Map;

/**
 * Pure bump classification and throttle for the local Blind (C10: client only, no packet, no public sound). A wall
 * bump is the rising edge of a hard horizontal collision (a glancing {@code collidedSoftly} slide never counts) while a
 * movement key is held; a head bump is a vertical collision while airborne with the space just above the head really
 * blocked (a step-up jump also flags a vertical collision). The throttle is per contact: the same wall/ceiling or the
 * same player bumps at most once per {@link #THROTTLE_TICKS}, but a different player is never swallowed; a backwards
 * clock (world change) never blocks the next bump.
 * 本地盲人的纯碰撞判定与节流（C10：仅客户端，无数据包，无公开声音）。撞墙指按住移动键时“硬”水平碰撞的上升沿
 * （擦墙滑行 {@code collidedSoftly} 不算）；撞头指离地时的竖直碰撞且头顶紧邻空间确实被挡住（跨台阶跳跃也会产生竖直碰撞）。
 * 节流按接触对象计算：同一墙面/天花板或同一玩家每 {@link #THROTTLE_TICKS} 刻至多一次，但不会吞掉与另一名玩家的接触；
 * 时钟倒退（换世界）不会阻挡下一次碰撞。
 */
public final class BlindBumpDetector {
    public static final int THROTTLE_TICKS = 8;
    /** Contact key of any block (wall or ceiling). / 任意方块（墙面或天花板）的接触键。 */
    public static final long ENVIRONMENT_CONTACT = Long.MIN_VALUE;

    public enum Bump {
        NONE,
        WALL,
        HEAD
    }

    private final Map<Long, Long> lastContactTicks = new HashMap<>();
    private boolean wasHardHorizontal;

    /**
     * Feeds one client tick of the local player's collision flags and returns the bump candidate; throttling happens
     * per contact in {@link #admit}.
     * 输入本地玩家一个客户端刻的碰撞标记并返回碰撞候选；节流由 {@link #admit} 按接触对象处理。
     */
    public Bump tick(boolean horizontalCollision, boolean collidedSoftly, boolean movementInput,
                     boolean verticalCollision, boolean onGround, boolean headBlocked) {
        boolean hardHorizontal = horizontalCollision && !collidedSoftly;
        boolean risingHorizontal = hardHorizontal && !wasHardHorizontal;
        wasHardHorizontal = hardHorizontal;
        return classify(risingHorizontal, movementInput, verticalCollision, onGround, headBlocked);
    }

    public static Bump classify(boolean risingHardHorizontal, boolean movementInput, boolean verticalCollision,
                                boolean onGround, boolean headBlocked) {
        if (risingHardHorizontal && movementInput) {
            return Bump.WALL;
        }
        if (verticalCollision && !onGround && headBlocked) {
            return Bump.HEAD;
        }
        return Bump.NONE;
    }

    /**
     * True, and remembered, unless the same contact already bumped within {@link #THROTTLE_TICKS}.
     * 除非同一接触对象在 {@link #THROTTLE_TICKS} 刻内已碰撞过，否则返回真并记录。
     */
    public boolean admit(long worldTick, long contact) {
        lastContactTicks.values().removeIf(last -> worldTick < last || worldTick - last >= THROTTLE_TICKS);
        if (lastContactTicks.containsKey(contact)) {
            return false;
        }
        lastContactTicks.put(contact, worldTick);
        return true;
    }

    /** Contact key of a player entity id; never {@link #ENVIRONMENT_CONTACT}. / 玩家实体 id 的接触键。 */
    public static long playerContact(int entityId) {
        return entityId;
    }

    public void reset() {
        wasHardHorizontal = false;
        lastContactTicks.clear();
    }

    /**
     * The box between two boxes: on each axis the gap where they are apart, else their overlap. A block collision in
     * it means a block separates the two boxes.
     * 两个碰撞箱之间的区域：在两者分开的轴上取间隙，否则取重叠部分。其中若有方块碰撞，说明两者被方块隔开。
     */
    public static Box between(Box a, Box b) {
        return new Box(
                Math.min(Math.max(a.minX, b.minX), Math.min(a.maxX, b.maxX)),
                Math.min(Math.max(a.minY, b.minY), Math.min(a.maxY, b.maxY)),
                Math.min(Math.max(a.minZ, b.minZ), Math.min(a.maxZ, b.maxZ)),
                Math.max(Math.max(a.minX, b.minX), Math.min(a.maxX, b.maxX)),
                Math.max(Math.max(a.minY, b.minY), Math.min(a.maxY, b.maxY)),
                Math.max(Math.max(a.minZ, b.minZ), Math.min(a.maxZ, b.maxZ)));
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
