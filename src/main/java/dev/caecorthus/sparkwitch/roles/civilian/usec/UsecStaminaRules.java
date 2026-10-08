package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.doctor4t.wathe.api.Role;
import org.jetbrains.annotations.Nullable;

/**
 * Pure USEC stamina-regeneration math (twice the police regen, stacking with traits). The cap needs no code: the role's
 * {@code maxSprintTime} is {@link UsecRules#MAX_SPRINT_TICKS} (twice the Vigilante's), which Wathe writes as the
 * {@code wathe:max_sprint_time} base every tick, so SparkTraits Excellent Physique's +1.0 ADD_MULTIPLIED_TOTAL doubles it
 * again. The regen boost doubles the whole per-tick gain measured from before Wathe's tick to after SparkTraits'
 * Excellent Physique bonus, so the two stack multiplicatively on purpose (2 × (0.25 + 0.25) = ×4 with that trait).
 * 纯 USEC 体力恢复计算（警察恢复的两倍，可与词条叠加）。上限无需代码：职业 {@code maxSprintTime} 为
 * {@link UsecRules#MAX_SPRINT_TICKS}（义警的两倍），Wathe 每刻把它写成 {@code wathe:max_sprint_time} 基础值，
 * SparkTraits 体质优异的 +1.0 ADD_MULTIPLIED_TOTAL 会再翻倍。恢复加成把“Wathe 体力刻之前”到“SparkTraits 体质优异
 * 加成之后”的整段每刻增量翻倍，因此两者有意按乘法叠加（带该词条时 2 × (0.25 + 0.25) = ×4）。
 */
public final class UsecStaminaRules {
    /** "Nothing captured this tick" marker. / “本刻未捕获”的标记。 */
    public static final float NOT_CAPTURED = Float.NaN;

    private UsecStaminaRules() {
    }

    /**
     * Side gate: every server player, and on a client only the local player (the one whose HUD shows this stamina).
     * 端侧门槛：服务端的所有玩家；客户端只处理本地玩家（其 HUD 显示这份体力）。
     */
    public static boolean tracksOnThisSide(boolean serverPlayer, boolean mainPlayer) {
        return serverPlayer || mainPlayer;
    }

    /**
     * Same gate as Wathe's own regen (running round, not spectator/creative) plus the exact USEC role.
     * 与 Wathe 自身恢复相同的门槛（对局进行中、非旁观/创造），外加身份恰为 USEC。
     */
    public static boolean appliesTo(boolean running, boolean aliveAndSurvival, @Nullable Role role) {
        return running && aliveAndSurvival && UsecRules.isUsec(role);
    }

    /**
     * Only a real regeneration tick is boosted: not sprinting, finite stamina, a positive gain from a finite,
     * non-negative capture. A tick that spent stamina (sprint stopped mid-tick) is never doubled.
     * 只放大真正的恢复刻：未疾跑、有限体力、从有限且非负的捕获值得到正增量。消耗体力的刻（刻中途停止疾跑）绝不翻倍。
     */
    public static boolean regenerates(boolean sprinting, boolean infiniteStamina, int maxSprintTime, float previous,
                                      float current) {
        return !sprinting && !infiniteStamina && maxSprintTime > 0
                && Float.isFinite(previous) && previous >= 0.0F && current > previous;
    }

    /**
     * {@code previous + (current - previous) × factor}, clamped to the cap; never below {@code current}.
     * {@code previous + (current - previous) × 倍率}，限制在上限内；绝不低于 {@code current}。
     */
    public static float boostedStamina(float previous, float current, int maxSprintTime) {
        float boosted = previous + (current - previous) * (float) UsecRules.STAMINA_REGEN_FACTOR;
        return Math.max(current, Math.min(boosted, (float) maxSprintTime));
    }
}
