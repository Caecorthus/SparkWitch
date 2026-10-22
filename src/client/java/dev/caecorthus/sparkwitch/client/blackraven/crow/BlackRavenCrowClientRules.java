package dev.caecorthus.sparkwitch.client.blackraven.crow;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowFlightRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowUseRules;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Pure client rules of Raven Form (化鸦): flight input and acceleration, the locked key set, the crow hotbar's feather
 * slot and the owner's HUD state machine. No world or client access, so it is unit-tested. Speeds come from the shared
 * {@link BlackRavenCrowFlightRules} (the server validates every predicted step with the same caps).
 * 化鸦的纯客户端规则：飞行输入与加速、被锁定的按键集合、乌鸦快捷栏的归羽格，以及本人 HUD 状态机。不访问世界或客户端，可单元测试。
 * 速度来自共享的 {@link BlackRavenCrowFlightRules}（服务端用相同上限校验每一步预测）。
 */
public final class BlackRavenCrowClientRules {
    /**
     * Share of the gap to the target velocity closed per tick: about 0.3 s from rest to 24 blocks/s, and the same to
     * stop. / 每刻向目标速度靠近的比例：从静止到 24 格/秒约 0.3 秒，停下同样如此。
     */
    public static final double ACCELERATION = 0.35;
    /** Below this squared speed (blocks/tick) with no input the crow stops. / 无输入且速度平方低于此值时停下。 */
    public static final double STOP_EPSILON_SQUARED = 1.0E-5;
    /** Crow look pitch limit. / 乌鸦俯仰角上限。 */
    public static final float MAX_PITCH = 89.0F;
    /**
     * Right-click reach from the crow's eye: the server's own {@link BlackRavenCrowUseRules#USE_REACH}, which it
     * re-validates from the crow with extra slack.
     * 从乌鸦眼睛起算的右键距离：即服务端的 {@link BlackRavenCrowUseRules#USE_REACH}，服务端会带余量重新校验。
     */
    public static final double USE_REACH = BlackRavenCrowUseRules.USE_REACH;
    /**
     * Ticks the feather slot shows empty after a drop request while the server confirmation is on its way; afterwards
     * an unconfirmed drop shows the feather again so the owner can retry.
     * 发送丢羽请求后、等待服务端确认期间归羽格显示为空的刻数；超时仍未确认则重新显示归羽，便于重试。
     */
    public static final int FEATHER_PENDING_TICKS = 20;
    /** Ticks the bound crow may be missing before the local view gives up. / 已绑定乌鸦缺失多少刻后本地放弃画面。 */
    public static final int MISSING_GRACE_TICKS = 40;
    /** Fail-safe cap on the post-session body hold. / 会话结束后本体保持的兜底上限。 */
    public static final int BODY_SETTLE_MAX_TICKS = 200;

    /**
     * Vanilla keys locked while flying (body actions and body movement). Everything else keeps working: chat, the
     * player list, F5/F1, the instinct key (the 8-block x-ray centres on the crow), Perception, key 2 (ends the flight)
     * and voice chat. Flight reads WASD/space/sneak physically, so locking them only keeps the body and other mods
     * from reacting.
     * 飞行期间锁定的原版按键（本体动作与本体移动）。其余按键照常：聊天、玩家列表、F5/F1、本能键（8 格透视以乌鸦为中心）、感知、
     * 技能键 2（提前结束）与语音。飞行直接读取 WASD/空格/潜行的物理状态，锁定它们只是让本体与其他模组不作反应。
     */
    public static final Set<String> LOCKED_KEYS = Set.of(
            "key.attack", "key.use", "key.pickItem", "key.drop", "key.swapOffhand", "key.inventory",
            "key.hotbar.1", "key.hotbar.2", "key.hotbar.3", "key.hotbar.4", "key.hotbar.5",
            "key.hotbar.6", "key.hotbar.7", "key.hotbar.8", "key.hotbar.9",
            "key.sprint", "key.forward", "key.back", "key.left", "key.right", "key.jump", "key.sneak");

    private BlackRavenCrowClientRules() {
    }

    public static boolean isLockedKey(@Nullable String translationKey) {
        return translationKey != null && LOCKED_KEYS.contains(translationKey);
    }

    /** Raw axis from two opposing keys. / 由一对相反按键得到的原始轴。 */
    public static double axis(boolean positive, boolean negative) {
        return positive == negative ? 0.0 : positive ? 1.0 : -1.0;
    }

    /**
     * Target velocity (blocks/tick) for the held keys: horizontal input moves along the crow's yaw at the full
     * {@link BlackRavenCrowFlightRules#HORIZONTAL_STEP} (diagonals normalised), jump/sneak climb or sink at
     * {@link BlackRavenCrowFlightRules#VERTICAL_STEP}. Same basis as vanilla {@code movementInputToVelocity}.
     * 按住按键对应的目标速度（格/刻）：水平输入沿乌鸦朝向以满速 HORIZONTAL_STEP 移动（斜向归一化），跳跃/潜行以 VERTICAL_STEP
     * 升降。坐标变换与原版 movementInputToVelocity 相同。
     */
    public static Vec3d targetVelocity(double forward, double strafe, double vertical, float yawDegrees) {
        double length = Math.sqrt(forward * forward + strafe * strafe);
        double f = 0.0;
        double s = 0.0;
        if (length > 0.0) {
            f = forward / length * BlackRavenCrowFlightRules.HORIZONTAL_STEP;
            s = strafe / length * BlackRavenCrowFlightRules.HORIZONTAL_STEP;
        }
        double yaw = yawDegrees * MathHelper.RADIANS_PER_DEGREE;
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        double up = Math.clamp(vertical, -1.0, 1.0) * BlackRavenCrowFlightRules.VERTICAL_STEP;
        return new Vec3d(s * cos - f * sin, up, f * cos + s * sin);
    }

    /**
     * One tick of smooth acceleration toward {@code target}, then capped like the server
     * ({@link BlackRavenCrowFlightRules#limitStep} with full budgets). With no input it settles to exactly zero.
     * 向目标速度平滑加速一刻，再按服务端同样的上限截断（满预算的 limitStep）。无输入时最终精确归零。
     */
    public static Vec3d nextVelocity(Vec3d current, Vec3d target) {
        Vec3d next = current.add(target.subtract(current).multiply(ACCELERATION));
        if (target.lengthSquared() == 0.0 && next.lengthSquared() < STOP_EPSILON_SQUARED) {
            return Vec3d.ZERO;
        }
        return BlackRavenCrowFlightRules.limitStep(next,
                BlackRavenCrowFlightRules.fullBudget(BlackRavenCrowFlightRules.HORIZONTAL_STEP),
                BlackRavenCrowFlightRules.fullBudget(BlackRavenCrowFlightRules.VERTICAL_STEP));
    }

    public static float clampPitch(float pitch) {
        return MathHelper.clamp(pitch, -MAX_PITCH, MAX_PITCH);
    }

    /**
     * The crow hotbar slot shows the Homing Feather until the server confirms a drop ({@code dropped}) or while a
     * request is pending. / 服务端确认已丢出（dropped）或请求待确认期间，乌鸦快捷栏不显示归羽。
     */
    public static boolean showsFeather(boolean dropped, int pendingTicks) {
        return !dropped && pendingTicks <= 0;
    }

    /** A drop press is sent only while the feather shows. / 只有归羽仍显示时，丢弃键才发送请求。 */
    public static boolean sendsDrop(boolean dropped, int pendingTicks) {
        return showsFeather(dropped, pendingTicks);
    }

    // ---- HUD (plan §2.11) / HUD（计划 §2.11） ----

    /** The Raven's own Raven Form line. / 黑羽鸦自己的化鸦状态行。 */
    public enum HudState {
        LOCKED,
        READY,
        FLYING,
        LANDING,
        COOLDOWN
    }

    /**
     * Ordered: a running session (FLYING), then crow-world's 3 s landing hold (LANDING, it starts together with the
     * cooldown), then LOCKED until two tasks are done, then COOLDOWN, else READY.
     * 有序判定：会话进行中（FLYING），其次 crow-world 的 3 秒落地定身（LANDING，与冷却同时开始），然后未完成两个任务时为 LOCKED，
     * 再次为 COOLDOWN，否则 READY。
     */
    public static HudState hudState(boolean unlocked, boolean active, long landingTicks, int cooldownTicks) {
        if (active) {
            return HudState.FLYING;
        }
        if (landingTicks > 0L) {
            return HudState.LANDING;
        }
        if (!unlocked) {
            return HudState.LOCKED;
        }
        return cooldownTicks > 0 ? HudState.COOLDOWN : HudState.READY;
    }

    /** Seconds shown for a tick count, rounded up. / 刻数对应的显示秒数（向上取整）。 */
    public static int seconds(long ticks) {
        return BlackRavenCrowRules.ceilSeconds(ticks);
    }
}
