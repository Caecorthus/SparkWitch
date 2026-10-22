package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import dev.doctor4t.wathe.game.GameConstants;

/**
 * Pure rules of the Raven Form landing (plan §0 and §2.8): a natural end (key 2 or the 30 s timeout) with a dropped
 * Homing Feather moves the player to the feather (the nearest safe spot, else the body) and holds them there for 3 s;
 * every other end, or no feather, returns to the body at once with no hold. During the hold the player is visible and
 * attackable, may turn the camera, and cannot move, use items or use skills.
 * 化鸦落地（计划 §0 与 §2.8）的纯规则：自然结束（技能键 2 或 30 秒到时）且已丢出归羽时，把玩家移到归羽处（找最近的安全点，找不到则留在
 * 本体）并定身 3 秒；其他结束方式或没有归羽时，立即回到本体、不定身。定身期间玩家可见、可被攻击、可以转视角，但不能移动、使用物品或技能。
 */
public final class BlackRavenCrowLandingRules {
    /** The 3 s landing hold. / 3 秒落地定身。 */
    public static final int HOLD_TICKS = GameConstants.getInTicks(0, 3);
    /**
     * Horizontal drift or rise beyond this pulls the held player back to the landing spot (knockback, a modified
     * client); falling is left to gravity. / 水平偏移或上升超过该值时把被定身玩家拉回落点；下落交给重力。
     */
    public static final double DRIFT_THRESHOLD = 0.5;
    /**
     * Drift beyond this is another system's teleport (a revive, a swap): the hold lets go instead of fighting it.
     * 偏移超过该值视为其他机制的传送（复活、交换）：定身直接结束，不与之争夺。
     */
    public static final double TELEPORT_THRESHOLD = 4.0;

    /** Swirl: at most this many feathers per tick at the start, none at the end. / 环绕：开始时每刻至多这么多根，结束时为零。 */
    public static final int SWIRL_MAX_PER_TICK = 3;
    /** Extra feathers in the landing burst. / 落地瞬间额外爆出的羽毛数。 */
    public static final int SWIRL_BURST = 14;
    /** Orbit radius around the player's axis, in blocks. / 绕玩家轴线的环绕半径（格）。 */
    public static final double SWIRL_RADIUS = 0.85;
    /**
     * Turn of a swirl feather's velocity per tick, in radians; the client particle applies the same turn, so a feather
     * launched tangentially at {@link #swirlSpeed()} circles the player. / 环绕羽毛速度每刻转过的角度（弧度）；客户端粒子按同一角度转向，
     * 因此以 {@link #swirlSpeed()} 切向发射的羽毛会绕玩家旋转。
     */
    public static final double SWIRL_TURN = 0.3;
    /** Feet-relative band the swirl spawns in. / 环绕羽毛生成的高度范围（相对脚底）。 */
    public static final double SWIRL_MIN_HEIGHT = 0.1;
    public static final double SWIRL_MAX_HEIGHT = 1.9;
    /** Slight lift or sink of each feather per tick. / 每根羽毛每刻的轻微升降。 */
    public static final double SWIRL_VERTICAL_DRIFT = 0.02;

    /** Landing sounds, vanilla only (plan §2.12): parrot flight and phantom flap at a lowered pitch. / 原版落地音效。 */
    public static final float PARROT_VOLUME = 1.0F;
    public static final float PARROT_PITCH = 0.6F;
    public static final float PHANTOM_VOLUME = 0.8F;
    public static final float PHANTOM_PITCH = 0.5F;

    private BlackRavenCrowLandingRules() {
    }

    /** What the end of a session does to the player. / 会话结束对玩家的处理。 */
    public enum Landing {
        /** Back in the body at once, no hold (no feather, an unnatural end, or a dead owner). / 立即回到本体，不定身。 */
        BODY,
        /** Look for a safe spot at the feather; land and hold, or fall back to the body. / 在归羽处找安全点；落地并定身，或回到本体。 */
        FEATHER
    }

    /**
     * Only a natural end ({@link BlackRavenCrowEndReason#usesHomingFeather()}) of a still-playing owner with a dropped
     * feather lands at the feather. / 只有仍在局内的主人以自然方式结束、且已丢出归羽时，才在归羽处落地。
     */
    public static Landing landing(boolean naturalEnd, boolean featherOut, boolean ownerPlaying) {
        return naturalEnd && featherOut && ownerPlaying ? Landing.FEATHER : Landing.BODY;
    }

    /** Hold step of one server tick. / 定身的单刻处理结果。 */
    public enum HoldStep {
        /** Nothing to correct. / 无需纠正。 */
        KEEP,
        /** Pull the player back to the landing spot. / 把玩家拉回落点。 */
        PULL_BACK,
        /** The hold is over (time up, the player left play, or another system teleported them). / 定身结束。 */
        RELEASE
    }

    /**
     * One server tick of the hold. {@code dx}/{@code dy}/{@code dz} are the player's offset from the landing spot.
     * 定身的单刻判定。{@code dx}/{@code dy}/{@code dz} 为玩家相对落点的偏移。
     */
    public static HoldStep holdStep(long now, long holdUntilTick, boolean participant, double dx, double dy,
                                    double dz) {
        if (!participant || now >= holdUntilTick) {
            return HoldStep.RELEASE;
        }
        double horizontal = dx * dx + dz * dz;
        if (horizontal + dy * dy > TELEPORT_THRESHOLD * TELEPORT_THRESHOLD) {
            return HoldStep.RELEASE;
        }
        return horizontal > DRIFT_THRESHOLD * DRIFT_THRESHOLD || dy > DRIFT_THRESHOLD
                ? HoldStep.PULL_BACK : HoldStep.KEEP;
    }

    /**
     * Feathers spawned this tick: from {@link #SWIRL_MAX_PER_TICK} at the start of the hold down to none at its end
     * (plan §0: many to none). / 本刻生成的羽毛数：从定身开始时的 {@link #SWIRL_MAX_PER_TICK} 逐渐减少到结束时的零（由多到无）。
     */
    public static int swirlCount(long remainingTicks) {
        if (remainingTicks <= 0) {
            return 0;
        }
        long clamped = Math.min(remainingTicks, HOLD_TICKS);
        return (int) Math.ceil(SWIRL_MAX_PER_TICK * (double) clamped / HOLD_TICKS);
    }

    /**
     * Tangential launch speed for a circle of {@link #SWIRL_RADIUS} when the velocity turns by {@link #SWIRL_TURN} every
     * tick (chord of the turn). / 每刻转向 {@link #SWIRL_TURN} 时、绕半径 {@link #SWIRL_RADIUS} 圆周运动所需的切向发射速度。
     */
    public static double swirlSpeed() {
        return 2.0 * SWIRL_RADIUS * Math.sin(SWIRL_TURN / 2.0);
    }
}
