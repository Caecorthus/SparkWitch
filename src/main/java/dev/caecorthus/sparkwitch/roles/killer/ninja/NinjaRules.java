package dev.caecorthus.sparkwitch.roles.killer.ninja;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameConstants;
import java.util.Set;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Pure Ninja tuning and kill predicates.
 * 忍者的纯数值与击杀判断集中在这里，运行时接线由外层服务负责。
 */
public final class NinjaRules {
    public static final Identifier ROLE_ID = Identifier.of("sparkwitch", "ninja");
    public static final Identifier PARRY_SKILL_ID = Identifier.of("sparkwitch", "ninja_parry");
    public static final int COLOR = 0x2C2C2C;
    public static final int PARRY_INITIAL_COOLDOWN_TICKS = 1200;
    // 忍者开局苦无先锁 60 秒，和出刀后的 30 秒使用冷却分开管理。
    public static final int NINJA_KNIFE_INITIAL_COOLDOWN_TICKS = 60 * 20;
    public static final int PARRY_WINDOW_TICKS = 50;
    public static final int PARRY_COOLDOWN_TICKS = 3600;
    public static final int DARK_KILL_BOUNTY = 150;
    public static final int DARKNESS_MAX_RAW_BRIGHTNESS = 5;
    public static final int NINJA_KNIFE_PRICE = 100;
    public static final int NINJA_SHURIKEN_PRICE = 275;
    public static final int LOCKPICK_PRICE = 50;
    // Grappling Hook (owner 2026-10-07): role-agnostic tool; the item cooldown only starts once a hook cycle ends.
    // 钩爪（所有者 2026-10-07）：不限职业的工具；物品冷却只在一次钩爪循环结束后开始。
    public static final int GRAPPLING_HOOK_PRICE = 100;
    public static final int GRAPPLING_HOOK_INITIAL_COOLDOWN_TICKS = 90 * 20;
    public static final int GRAPPLING_HOOK_COOLDOWN_TICKS = 10 * 20;
    public static final double GRAPPLING_HOOK_RANGE = 24.0;
    public static final double GRAPPLING_HOOK_FLIGHT_SPEED = 2.5;
    public static final int GRAPPLING_HOOK_LATCH_TICKS = 5 * 20;
    public static final double GRAPPLING_HOOK_BREAK_DISTANCE = 32.0;
    public static final double GRAPPLING_HOOK_PULL_SPEED = 1.4;
    public static final double GRAPPLING_HOOK_ARRIVE_DISTANCE = 0.9;
    public static final int GRAPPLING_HOOK_PULL_MAX_TICKS = 40;
    /** A pull with no progress for this many ticks has stalled. / 连续这么多刻没有进展即视为卡住。 */
    public static final int GRAPPLING_HOOK_STALL_TICKS = 5;
    /**
     * The stall check waits this long so the first velocity packet's client round trip is not read as a stall.
     * 卡住判定先等待这段时间，避免把首个速度包的客户端往返延迟误判为卡住。
     */
    public static final int GRAPPLING_HOOK_STALL_GRACE_TICKS = 10;
    public static final double GRAPPLING_HOOK_MIN_PROGRESS = 0.2;
    public static final double GRAPPLING_HOOK_WALL_OFFSET = 0.5;
    public static final double GRAPPLING_HOOK_CEILING_CLEARANCE = 0.1;
    private static final Set<Identifier> UNPARRYABLE_WATHE_DEATH_REASONS = Set.of(
            GameConstants.DeathReasons.GUN_BACKFIRE,
            GameConstants.DeathReasons.FELL_OUT_OF_TRAIN,
            GameConstants.DeathReasons.ESCAPED,
            GameConstants.DeathReasons.SHOT_INNOCENT,
            GameConstants.DeathReasons.MENTAL_BREAKDOWN,
            GameConstants.DeathReasons.VANILLA_DEATH,
            GameConstants.DeathReasons.DROWNED
    );

    private NinjaRules() {
    }

    public static boolean isNinja(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    public static boolean shouldParryPlayerKill(
            boolean ninjaVictim,
            boolean parryActive,
            boolean selfKill,
            @Nullable Identifier deathReason
    ) {
        return ninjaVictim
                && parryActive
                && !selfKill
                && deathReason != null
                && !UNPARRYABLE_WATHE_DEATH_REASONS.contains(deathReason);
    }

    public static boolean isDarkKillLocation(int rawBrightness, boolean blackoutActive) {
        return blackoutActive || rawBrightness <= DARKNESS_MAX_RAW_BRIGHTNESS;
    }

    /**
     * Where the puller's feet should end for a latch on {@code side}: a floor puts the feet on the hit point, a
     * ceiling puts the head just under it, a wall centres the body on the hook, set off the face so it stays clear.
     * 钩在 {@code side} 面时拉拽者双脚的终点：地面让双脚落在钩点；天花板让头部恰在其下；墙面让身体中心对准钩点，
     * 并沿面法线外移以免嵌进墙里。
     */
    public static Vec3d grappleFeetTarget(Vec3d latch, Direction side, double playerHeight) {
        return switch (side) {
            case UP -> latch;
            case DOWN -> latch.add(0.0, -(playerHeight + GRAPPLING_HOOK_CEILING_CLEARANCE), 0.0);
            default -> latch.add(
                    side.getOffsetX() * GRAPPLING_HOOK_WALL_OFFSET,
                    -playerHeight / 2.0,
                    side.getOffsetZ() * GRAPPLING_HOOK_WALL_OFFSET
            );
        };
    }

    /** Pull speed for the remaining distance, never overshooting the target. / 按剩余距离计算的拉拽速度，不越过终点。 */
    public static double grapplePullSpeed(double remainingDistance) {
        return Math.max(0.0, Math.min(GRAPPLING_HOOK_PULL_SPEED, remainingDistance));
    }

    public static boolean hasGrappleArrived(double remainingDistance) {
        return remainingDistance < GRAPPLING_HOOK_ARRIVE_DISTANCE;
    }

    public static boolean isGrapplePullTimedOut(int pullTicks) {
        return pullTicks >= GRAPPLING_HOOK_PULL_MAX_TICKS;
    }

    /**
     * True once the grace has passed and the best distance has not improved for the stall window.
     * 宽限期过后、最佳距离在卡住窗口内未改善时为真。
     */
    public static boolean isGrapplePullStalled(int pullTicks, int ticksWithoutProgress) {
        return pullTicks >= GRAPPLING_HOOK_STALL_GRACE_TICKS && ticksWithoutProgress >= GRAPPLING_HOOK_STALL_TICKS;
    }

    /** Progress counts only when the distance beats the best so far by a margin. / 只有距离比此前最佳值缩短足够幅度才算进展。 */
    public static boolean isGrappleProgress(double bestDistance, double distance) {
        return distance <= bestDistance - GRAPPLING_HOOK_MIN_PROGRESS;
    }

    public static boolean isGrappleLatchExpired(int latchedTicks) {
        return latchedTicks >= GRAPPLING_HOOK_LATCH_TICKS;
    }

    public static boolean isGrappleChainOverstretched(double ownerToHookDistance) {
        return ownerToHookDistance > GRAPPLING_HOOK_BREAK_DISTANCE;
    }
}
