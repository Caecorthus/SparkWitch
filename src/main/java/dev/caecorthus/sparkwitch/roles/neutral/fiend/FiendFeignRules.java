package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Pure feign-death decisions (owner request 2026-10-16): values in, values out, no world access.
 * 假死的纯规则判断（所有者 2026-10-16 需求）：输入数值、输出数值，不访问世界。
 */
public final class FiendFeignRules {
    /**
     * Death reasons a periodic fake corpse may show (owner pick: a random common one). Wathe-core murder reasons only,
     * so the corpse never names a role that may be absent from the round; the grenade is left out because a silent
     * explosion would give the fake away.
     * 定时假死尸体可显示的死因（所有者选择：随机常见死因）。只取 Wathe 本体的谋杀死因，尸体因此不会指向本局可能不存在的职业；
     * 手雷不在其中，因为没有声响的爆炸会暴露假死。
     */
    public static final List<Identifier> PERIODIC_REASONS = List.of(
            GameConstants.DeathReasons.KNIFE,
            GameConstants.DeathReasons.GUN,
            GameConstants.DeathReasons.POISON,
            GameConstants.DeathReasons.BAT
    );

    /** Why a feign death starts. / 假死的起因。 */
    public enum Trigger {
        PERIODIC(FiendRules.FEIGN_TICKS),
        LETHAL_HIT(FiendRules.LETHAL_FEIGN_TICKS);

        private final int durationTicks;

        Trigger(int durationTicks) {
            this.durationTicks = durationTicks;
        }

        public int durationTicks() {
            return durationTicks;
        }
    }

    private FiendFeignRules() {
    }

    /**
     * Whether a Fiend may start a feign death now: a dormant Fiend in an ACTIVE round, standing in the world (not a
     * spectator, not creative), not already feigning, and not held by another session that owns its camera or game
     * mode (Taotie stomach, Rift Gate, Kidnapper control). Busy Fiends simply wait.
     * 魔人此刻能否开始假死：ACTIVE 回合中的休眠魔人，本人站在世界中（非旁观、非创造），尚未假死，且未被其他占用其视角或
     * 游戏模式的状态控制（饕餮胃中、裂隙门内、绑匪控制）。忙碌时只是等待。
     */
    public static boolean canStart(boolean dormantFiend, boolean gameActive, boolean inWorld, boolean feigning,
                                   boolean swallowed, boolean otherSession) {
        return dormantFiend && gameActive && inWorld && !feigning && !swallowed && !otherSession;
    }

    /**
     * A feigning Fiend cannot move, so a fall can only come from its spectator position being carried off the train;
     * the dormant immunity therefore also blocks {@code wathe:fell_out_of_train} while it feigns. Disconnect,
     * {@code /kill} and the Vulture's {@code sparkwitch:fiend_devoured} still kill.
     * 假死中的魔人无法移动，坠落只可能来自其旁观位置被带出列车；因此假死期间休眠免疫也会拦截
     * {@code wathe:fell_out_of_train}。断线、{@code /kill} 与秃鹫的 {@code sparkwitch:fiend_devoured} 仍会致死。
     */
    public static boolean shieldsWhileFeigning(boolean feigning, @Nullable Identifier deathReason) {
        return feigning && GameConstants.DeathReasons.FELL_OUT_OF_TRAIN.equals(deathReason);
    }

    /**
     * More than a jump's worth of fall distance: the Fiend is dropping (e.g. pushed off the train), so a periodic
     * feign waits rather than freezing it mid-air.
     * 坠落距离超过一次跳跃：魔人正在下坠（例如被推下列车），定时假死因此等待，而不是把它冻在半空。
     */
    public static final float FALLING_DISTANCE = 2.0F;

    public static boolean isFalling(float fallDistance) {
        return fallDistance > FALLING_DISTANCE;
    }

    /**
     * A lethal hit that also launched the Fiend (Mighty Force sets the knockback this tick, then kills) lets the push
     * play out first: the 20 s feign waits until the Fiend lands, at most {@link #LAUNCH_MAX_WAIT_TICKS}, so a push off
     * the train still kills it.
     * 同时击飞魔人的致命命中（巨力冲击本刻先设置击退再击杀）先让击退生效：20 秒假死等魔人落地后再开始，最多等待
     * {@link #LAUNCH_MAX_WAIT_TICKS}，因此把它推下列车仍能杀死它。
     */
    public static final double LAUNCH_SPEED_SQUARED = 0.25;
    public static final int LAUNCH_SETTLE_TICKS = 2;
    public static final int LAUNCH_MAX_WAIT_TICKS = 60;

    /** Velocity set by something else this tick and fast enough to be a launch. / 本刻被外部设置且足够快的速度即视为击飞。 */
    public static boolean isLaunched(boolean velocityModified, double speedSquared) {
        return velocityModified && speedSquared > LAUNCH_SPEED_SQUARED;
    }

    /** A launched Fiend's deferred lethal feign starts once it has landed, or after the wait cap. / 被击飞魔人延后的致命假死在其落地后或等待上限到达时开始。 */
    public static boolean launchSettled(long now, long launchedAt, boolean onGround, boolean falling) {
        long waited = now - launchedAt;
        return waited >= LAUNCH_MAX_WAIT_TICKS || waited >= LAUNCH_SETTLE_TICKS && onGround && !falling;
    }

    /** A periodic feign is due once the scheduled tick is reached. / 到达排定 tick 即触发定时假死。 */
    public static boolean periodicDue(long now, long dueTick) {
        return now >= dueTick;
    }

    /** First feign: 60 s after the round start; later ones 60 s after each revival. / 首次在开局 60 秒后，之后每次复活后 60 秒。 */
    public static long nextDueTick(long now) {
        return now + FiendRules.FEIGN_INTERVAL_TICKS;
    }

    /**
     * The fake corpse's reason: the blocked kill's own reason for a lethal hit (falls back to the periodic pool when
     * missing), otherwise one drawn from {@link #PERIODIC_REASONS}.
     * 假尸体的死因：致命命中时使用被挡下击杀自身的死因（缺失时回退到定时池），否则从 {@link #PERIODIC_REASONS} 中随机抽取。
     */
    public static Identifier corpseReason(Trigger trigger, @Nullable Identifier hitReason, RandomGenerator random) {
        if (trigger == Trigger.LETHAL_HIT && hitReason != null) {
            return hitReason;
        }
        return PERIODIC_REASONS.get(random.nextInt(PERIODIC_REASONS.size()));
    }

    /** Remaining ticks clamped at 0. / 剩余 tick，最小为 0。 */
    public static int remaining(long now, long tick) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, tick - now));
    }
}
