package dev.caecorthus.sparkwitch.roles.killer.bellringer;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Stable gameplay values and pure gates owned by the Bell Ringer.
 * 敲钟人拥有的稳定玩法数值与纯规则判断。
 */
public final class BellRingerRules {
    public static final Identifier ROLE_ID = SparkWitch.id("bell_ringer");
    public static final Identifier ECHO_SKILL_ID = SparkWitch.id("bell_echo");
    public static final int COLOR = 0x8C6D3F;

    public static final int ECHO_INITIAL_COOLDOWN_TICKS = 120 * 20;
    public static final int ECHO_COOLDOWN_TICKS = 90 * 20;
    /** Wathe round time spent per cast (45 s). / 每次发动消耗的 Wathe 对局时间（45 秒）。 */
    public static final int ECHO_TIME_COST_TICKS = 45 * 20;
    /** Cast is allowed only while strictly more than this remains (60 s). / 剩余时间必须严格大于该值（60 秒）才可发动。 */
    public static final int ECHO_MIN_REMAINING_TICKS = 60 * 20;
    public static final int ECHO_DEADLINE_TICKS = 60 * 20;
    public static final int ECHO_HINT_TICKS = 60 * 20;
    public static final float ECHO_DRAIN_MULTIPLIER = 1.5F;
    /** Flat half of the normal 1.0 mood maximum; may be lethal. / 固定扣除常规理智上限 1.0 的一半，可能致死。 */
    public static final float ECHO_FAILURE_MOOD_LOSS = 0.5F;
    public static final int TOLL_COOLDOWN_TICKS = 30 * 20;
    public static final int TOLL_SCAN_INTERVAL_TICKS = 10;

    private BellRingerRules() {
    }

    /** Exact-role gate; never inferred from faction or namespace. / 仅按精确职业判断，不从阵营或命名空间推断。 */
    public static boolean isBellRinger(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    public static boolean canAffordEcho(int remainingTicks) {
        return remainingTicks > ECHO_MIN_REMAINING_TICKS;
    }

    public static boolean isDeadlinePassed(long now, long deadline) {
        return now >= deadline;
    }

    /**
     * Owner-approved exception: only the forced bell toll pierces protections that ignore Wathe's
     * {@code force} flag (e.g. Saint's HEAD guard); every other kill keeps its existing protection path.
     * 所有者批准的例外：仅强制的丧钟击杀可穿透忽略 Wathe {@code force} 标记的保护（如圣徒的 HEAD 拦截）；
     * 其他击杀的保护流程保持不变。
     */
    public static boolean piercesProtection(@Nullable Identifier deathReason, boolean force) {
        return force && SparkWitchDeathReasons.BELL_TOLL.equals(deathReason);
    }

    /**
     * Pure Echo audience split. Only living participants with real sanity (base mood not NONE and
     * effective civilian faction) whom the structural affect policy allows receive the forced task;
     * every other living participant only hears the bell. Callers must treat the caster as a hearer.
     * 回响受众的纯判断：仅“基础情绪非 NONE 且有效阵营为平民”、并被结构性影响策略允许的存活参与者获得强制任务；
     * 其余存活参与者只听到钟声。调用方需将施法者本人视为听者。
     */
    public static Audience classify(
            boolean participant,
            @Nullable Role.MoodType baseMood,
            @Nullable Identifier effectiveFaction,
            boolean affectAllowed
    ) {
        if (!participant) {
            return Audience.NONE;
        }
        boolean realSanity = baseMood != null
                && baseMood != Role.MoodType.NONE
                && FactionIds.CIVILIAN.equals(effectiveFaction);
        return realSanity && affectAllowed ? Audience.AFFECTED : Audience.HEARER;
    }

    /** Echo audience buckets. / 回响受众分类。 */
    public enum Audience {
        /** Receives the forced Echo task. / 获得强制回响任务。 */
        AFFECTED,
        /** Only sees the red "heard the bell" hint. / 只显示红色“听到钟声”提示。 */
        HEARER,
        /** Not a living participant; unaffected. / 非存活参与者，不受影响。 */
        NONE
    }
}
