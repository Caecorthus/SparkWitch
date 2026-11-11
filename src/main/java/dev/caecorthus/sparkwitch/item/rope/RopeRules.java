package dev.caecorthus.sparkwitch.item.rope;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.util.Identifier;

/**
 * Every number and every pure decision of the Rope ({@code sparkwitch:rope}, 二阶堂希罗's shop item; anyone holding it
 * may use it). Owner spec (2026-11-11): use it while aiming at a player within {@link #RANGE} blocks whose back is
 * toward the user (the user stands inside the {@link #BACK_HALF_ARC_DEGREES} half-arc behind the target); both are
 * held for {@link #CHANNEL_TICKS}, the target takes a cosmetic hit every {@link #HIT_INTERVAL_TICKS}, then dies of
 * {@code sparkwitch:strangled}, passing shields without consuming them. An attacked, controlled or displaced user
 * aborts and the rope cools down for {@link #INTERRUPT_COOLDOWN_TICKS}; the rope is consumed only by a successful
 * kill. No Minecraft state is read here, so every rule is unit-testable.
 * 绳索（{@code sparkwitch:rope}，二阶堂希罗的商店道具；任何持有者都可使用）的全部数值与纯判定。所有者规格（2026-11-11）：瞄准
 * {@link #RANGE} 格内背对使用者的玩家使用（使用者位于目标身后 {@link #BACK_HALF_ARC_DEGREES} 的半弧内）；双方定身
 * {@link #CHANNEL_TICKS}，目标每 {@link #HIT_INTERVAL_TICKS} 受到一次仅表现的受击，随后以 {@code sparkwitch:strangled} 死亡，
 * 无视护盾且不消耗护盾。使用者被攻击、被控制或被移位时中断，绳索进入 {@link #INTERRUPT_COOLDOWN_TICKS} 冷却；只有击杀成功才消耗
 * 绳索。本类不读取任何 Minecraft 状态，因此每条规则都可单元测试。
 */
public final class RopeRules {
    public static final Identifier ITEM_ID = SparkWitch.id("rope");
    public static final double RANGE = 2.0D;
    // Half-width of the arc behind the target, measured from straight behind. / 目标身后弧的半角，从正后方算起。
    public static final double BACK_HALF_ARC_DEGREES = 60.0D;
    public static final int CHANNEL_TICKS = 60;
    public static final int HIT_INTERVAL_TICKS = 20;
    public static final int INTERRUPT_COOLDOWN_TICKS = 200;
    public static final int PRICE = 50;

    /** Hit-volume margin of the aim ray, the Taser's. / 瞄准射线的命中体积余量，与电击枪相同。 */
    public static final double TARGET_BOX_EXPANSION = 0.2D;
    /** The choke is sent to, and fades out at, this many blocks. / 窒息声只发送到这么多格内，并在该距离衰减为零。 */
    public static final float CHOKE_SOUND_RANGE = 8.0F;
    /** A held player further than this from the anchor is pulled back. / 被定身者偏离锚点超过该距离即被拉回。 */
    public static final double DRIFT_THRESHOLD = 0.25D;
    /**
     * A held player further than this from the anchor was moved by something else (a push, a teleport): the strangle
     * breaks. Our own pull-backs land on the anchor, so they never count.
     * 被定身者偏离锚点超过该距离，说明被其他机制移动（推搡、传送）：勒杀中断。我们自己的拉回落在锚点上，因此从不计入。
     */
    public static final double DISPLACEMENT_TOLERANCE = 1.0D;
    /** Floating-point slack of the arc boundary, in degrees. / 弧边界的浮点容差（度）。 */
    private static final double ARC_EPSILON_DEGREES = 1.0E-6D;
    private static final double SAME_SPOT_SQUARED = 1.0E-8D;

    private RopeRules() {
    }

    /**
     * Angle in degrees (0..180) between the horizontal direction target → user and the direction straight behind the
     * target's look ({@code targetYaw} is Minecraft yaw: 0 looks toward +Z, 90 toward -X; any real value, wrapped
     * here). 0 means exactly behind, 180 exactly in front. 180 when the two stand on the same vertical line or an
     * input is not finite, so that case never counts as behind.
     * 目标→使用者的水平方向与目标视线正后方之间的夹角（度，0..180）；{@code targetYaw} 为 Minecraft 偏航角（0 朝 +Z，90 朝 -X；
     * 任意实数，在此归一化）。0 表示正后方，180 表示正前方。两人处于同一竖直线或输入非有限值时返回 180，因此这种情况永不算作背后。
     */
    public static double rearOffsetDegrees(double targetX, double targetZ, float targetYaw, double userX,
                                           double userZ) {
        double dx = userX - targetX;
        double dz = userZ - targetZ;
        if (!Double.isFinite(dx) || !Double.isFinite(dz) || !Float.isFinite(targetYaw)
                || dx * dx + dz * dz < SAME_SPOT_SQUARED) {
            return 180.0D;
        }
        // Minecraft yaw of the direction target → user. / 目标→使用者方向的 Minecraft 偏航角。
        double towardUser = Math.toDegrees(Math.atan2(-dx, dz));
        double offset = (towardUser - (targetYaw + 180.0D)) % 360.0D;
        if (offset > 180.0D) {
            offset -= 360.0D;
        } else if (offset < -180.0D) {
            offset += 360.0D;
        }
        return Math.abs(offset);
    }

    /**
     * The back check: the user stands in the target's rear arc, the ±{@link #BACK_HALF_ARC_DEGREES} boundary included.
     * 背后判定：使用者位于目标身后弧内，包含 ±{@link #BACK_HALF_ARC_DEGREES} 的边界。
     */
    public static boolean isBehind(double targetX, double targetZ, float targetYaw, double userX, double userZ) {
        return rearOffsetDegrees(targetX, targetZ, targetYaw, userX, userZ)
                <= BACK_HALF_ARC_DEGREES + ARC_EPSILON_DEGREES;
    }

    /** Why a use starts nothing; every refusal is free. / 使用未能开始的原因；任何拒绝都没有代价。 */
    public enum Refusal {
        /** The strangle starts. / 开始勒杀。 */
        NONE,
        /** The user may not act now (not a living participant, held, feared, ...). / 使用者此刻无法行动。 */
        USER_BLOCKED,
        /** No eligible player on the aim ray within reach. / 瞄准线上、距离内没有合格玩家。 */
        NO_TARGET,
        /** The aimed player does not have their back toward the user. / 被瞄准的玩家没有背对使用者。 */
        NOT_BEHIND
    }

    /** Gate order of a use: user, then target, then the back check. / 使用的判定顺序：使用者、目标、背后判定。 */
    public static Refusal refusal(boolean userMayAct, boolean hasTarget, boolean behind) {
        if (!userMayAct) {
            return Refusal.USER_BLOCKED;
        }
        if (!hasTarget) {
            return Refusal.NO_TARGET;
        }
        return behind ? Refusal.NONE : Refusal.NOT_BEHIND;
    }

    /** A cosmetic hit lands at ticks 20, 40 and 60 after the start. / 开始后的第 20、40、60 刻各有一次表现用受击。 */
    public static boolean isHitTick(int elapsedTicks) {
        return elapsedTicks > 0 && elapsedTicks <= CHANNEL_TICKS && elapsedTicks % HIT_INTERVAL_TICKS == 0;
    }

    /** The victim dies once the channel is complete. / 引导完成时目标死亡。 */
    public static boolean isComplete(int elapsedTicks) {
        return elapsedTicks >= CHANNEL_TICKS;
    }

    /** Whole seconds left, for the progress line (3, 2, 1, 0). / 进度提示用的剩余整秒数（3、2、1、0）。 */
    public static int secondsLeft(int elapsedTicks) {
        int left = CHANNEL_TICKS - Math.max(0, elapsedTicks);
        return left <= 0 ? 0 : (left + HIT_INTERVAL_TICKS - 1) / HIT_INTERVAL_TICKS;
    }

    /** One server tick of a held player's anchor. / 被定身者锚点的单刻处理结果。 */
    public enum HoldStep {
        /** Nothing to correct. / 无需纠正。 */
        KEEP,
        /** Gravity moved the player down: the anchor follows. / 重力使玩家下落：锚点随之下移。 */
        FOLLOW_FALL,
        /** A small drift: pull the player back to the anchor. / 小幅漂移：把玩家拉回锚点。 */
        PULL_BACK,
        /** Moved by something else beyond the tolerance. / 被其他机制移动且超出容差。 */
        DISPLACED
    }

    /**
     * {@code dx}/{@code dy}/{@code dz} are the player's offset from the anchor. Falling never counts as displacement
     * (a player caught mid-jump lands); a sideways or upward offset beyond {@link #DISPLACEMENT_TOLERANCE} does.
     * {@code dx}/{@code dy}/{@code dz} 为玩家相对锚点的偏移。下落永不算作移位（跳跃中被定身的玩家会落地）；水平或向上的偏移超过
     * {@link #DISPLACEMENT_TOLERANCE} 才算。
     */
    public static HoldStep holdStep(double dx, double dy, double dz) {
        double horizontal = dx * dx + dz * dz;
        double rise = Math.max(0.0D, dy);
        if (!Double.isFinite(horizontal) || !Double.isFinite(dy)
                || horizontal + rise * rise > DISPLACEMENT_TOLERANCE * DISPLACEMENT_TOLERANCE) {
            return HoldStep.DISPLACED;
        }
        if (horizontal > DRIFT_THRESHOLD * DRIFT_THRESHOLD || dy > DRIFT_THRESHOLD) {
            return HoldStep.PULL_BACK;
        }
        return dy < 0.0D ? HoldStep.FOLLOW_FALL : HoldStep.KEEP;
    }

    /** Why a running strangle stops before its kill. / 进行中的勒杀在击杀前停止的原因。 */
    public enum Interrupt {
        /** Keep strangling. / 继续勒杀。 */
        NONE,
        /** The user died, left or stopped being a living participant. / 使用者死亡、离开或不再是存活参与者。 */
        USER_GONE,
        /** The victim died (someone else's kill), left or stopped being a living participant. / 目标死亡、离开或不再是存活参与者。 */
        VICTIM_GONE,
        /** Another player attempted to kill the user, or {@code interruptUser} was called. / 他人试图击杀使用者，或调用了中止接缝。 */
        USER_ATTACKED,
        /** The user was stunned, feared, hypnotised, grabbed or otherwise controlled. / 使用者被眩晕、恐惧、催眠、抓取或以其他方式被控制。 */
        USER_CONTROLLED,
        /** The rope left the user's hands. / 绳索离开了使用者的手。 */
        ROPE_LOST,
        /** A push or teleport moved the user off the anchor. / 推搡或传送使使用者离开锚点。 */
        USER_DISPLACED,
        /** A push or teleport moved the victim out of the grip. / 推搡或传送使目标脱离控制。 */
        VICTIM_DISPLACED
    }

    /**
     * Per-tick classification, most decisive first: a missing player ends everything, then the user's own state, then
     * the victim's position. / 每刻的分类，按决定性从高到低：有人不在则直接结束，其次是使用者自身状态，最后是目标位置。
     */
    public static Interrupt interrupt(boolean userPresent, boolean victimPresent, boolean userControlled,
                                      boolean userHoldsRope, boolean userDisplaced, boolean victimDisplaced) {
        if (!userPresent) {
            return Interrupt.USER_GONE;
        }
        if (!victimPresent) {
            return Interrupt.VICTIM_GONE;
        }
        if (userControlled) {
            return Interrupt.USER_CONTROLLED;
        }
        if (!userHoldsRope) {
            return Interrupt.ROPE_LOST;
        }
        if (userDisplaced) {
            return Interrupt.USER_DISPLACED;
        }
        return victimDisplaced ? Interrupt.VICTIM_DISPLACED : Interrupt.NONE;
    }

    /**
     * Whether the user still pays the interrupt cooldown: always, except when the user is the one who is gone (the
     * rope of a dead player is deleted anyway). / 使用者是否仍进入打断冷却：除使用者本人已不在（死亡玩家的绳索本就会被删除）外一律进入。
     */
    public static boolean interruptCoolsDown(Interrupt interrupt) {
        return interrupt != Interrupt.NONE && interrupt != Interrupt.USER_GONE;
    }

    /** What the finished channel did to the rope. / 引导完成后绳索的结果。 */
    public enum Outcome {
        /** The kill succeeded: the rope is consumed, no cooldown. / 击杀成功：消耗绳索，无冷却。 */
        CONSUMED,
        /** A protection saved the victim: the rope is kept and cools down. / 目标被保护救下：保留绳索并进入冷却。 */
        KEPT_ON_COOLDOWN
    }

    /**
     * The kill succeeded when the victim is dead in Wathe and no SparkTraits Last Stand is about to revive them, or
     * when 死亡回归 turned the kill into one of Hiro's fake deaths. Every other survival (a trait, a role immunity, a
     * veto) keeps the rope and starts the interrupt cooldown.
     * 击杀成功的条件：目标在 Wathe 中已死亡且没有 SparkTraits 背水一战即将复活，或「死亡回归」把这次击杀变成了希罗的假死。其余任何
     * 幸存（词条、职业免疫、否决）都保留绳索并进入打断冷却。
     */
    public static Outcome outcome(boolean victimDeadInWathe, boolean lastStandPending, boolean feignedByHiro) {
        return feignedByHiro || victimDeadInWathe && !lastStandPending ? Outcome.CONSUMED : Outcome.KEPT_ON_COOLDOWN;
    }
}
