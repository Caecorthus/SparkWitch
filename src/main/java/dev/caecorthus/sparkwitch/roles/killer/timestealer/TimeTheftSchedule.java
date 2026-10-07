package dev.caecorthus.sparkwitch.roles.killer.timestealer;

/**
 * Pure curse timeline (owner decision Q1): which stages are newly due for a victim, when each stage began, how much of
 * the curse's own Slowness should still be left, and the private chime pitch. It never touches Minecraft types, so the
 * stage counter (not the live Slowness amplifier, which vanilla merges in place) is the only thing that decides death.
 * 纯诅咒时间轴（所有者决定 Q1）：受害者哪些阶段新到期、每个阶段何时开始、诅咒自身的缓慢理应剩余多少，以及私有钟声的音高。
 * 不接触任何 Minecraft 类型，因此决定死亡的只有阶段计数（而不是会被原版原地合并的实时缓慢等级）。
 */
final class TimeTheftSchedule {
    /** Highest stage that applies Slowness; the next one is lethal. / 施加缓慢的最高阶段；下一阶段即致死。 */
    static final int LAST_SLOWNESS_STAGE = TimeStealerRules.LETHAL_STAGE - 1;
    /** Chime pitch of stage 1, rising by {@link #CHIME_PITCH_STEP} per stage. / 第 1 阶钟声音高，每阶升高 {@link #CHIME_PITCH_STEP}。 */
    static final float CHIME_BASE_PITCH = 0.8F;
    static final float CHIME_PITCH_STEP = 0.15F;
    static final float FINAL_PITCH = 1.0F;

    private TimeTheftSchedule() {
    }

    /**
     * The work due now for a curse whose last applied stage is {@code appliedStage}. At the lethal moment no further
     * Slowness stage is returned (it would be removed again in the same tick); otherwise every stage between the
     * applied one and the due one is returned once, so a stage is never applied twice and never skipped.
     * 对最近已施加阶段为 {@code appliedStage} 的诅咒，返回此刻应做的事。致死时刻不再返回任何缓慢阶段（同一 tick 内会被立即移除）；
     * 否则已施加阶段与到期阶段之间的每个阶段各返回一次，因此阶段既不会重复施加，也不会被跳过。
     */
    static Step step(int appliedStage, long elapsedTicks) {
        int applied = Math.clamp(appliedStage, 0, LAST_SLOWNESS_STAGE);
        int due = TimeStealerRules.dueStage(Math.max(0L, elapsedTicks));
        if (TimeStealerRules.isLethal(due)) {
            return Step.LETHAL;
        }
        if (due <= applied) {
            return Step.NONE;
        }
        return new Step(applied + 1, due, false);
    }

    /** Elapsed ticks at which {@code stage} (1..4) begins. / {@code stage}（1..4）开始时的经过 tick。 */
    static long stageStartTicks(int stage) {
        return TimeStealerRules.GRACE_TICKS + (long) (Math.max(1, stage) - 1) * TimeStealerRules.STEP_TICKS;
    }

    /** Slowness amplifier applied by {@code stage} (1..4). / {@code stage}（1..4）施加的缓慢放大器。 */
    static int amplifier(int stage) {
        return Math.clamp(stage, 1, LAST_SLOWNESS_STAGE) - 1;
    }

    /**
     * Ticks of the curse's own Slowness that should remain after {@code elapsedTicks}, given that {@code stage} was
     * applied on time; 0 when nothing of ours can be left (no stage yet, or it already ran out).
     * 在 {@code stage} 按时施加的前提下，经过 {@code elapsedTicks} 后诅咒自身缓慢理应剩余的 tick；
     * 若不可能还有我们的缓慢（尚无阶段或已耗尽）则为 0。
     */
    static int expectedSlownessRemaining(int stage, long elapsedTicks) {
        if (stage < 1) {
            return 0;
        }
        long sinceApplied = Math.max(0L, elapsedTicks - stageStartTicks(stage));
        return (int) Math.max(0L, TimeStealerRules.SLOWNESS_DURATION_TICKS - sinceApplied);
    }

    /** Private chime pitch, rising stage by stage. / 私有钟声音高，逐阶升高。 */
    static float chimePitch(int stage) {
        return CHIME_BASE_PITCH + CHIME_PITCH_STEP * (Math.clamp(stage, 1, LAST_SLOWNESS_STAGE) - 1);
    }

    /**
     * Newly due stages {@code firstStage..lastStage} (inclusive; empty when {@code lastStage < firstStage}), or the
     * lethal settle.
     * 新到期的阶段 {@code firstStage..lastStage}（含两端；{@code lastStage < firstStage} 时为空），或致死结算。
     */
    record Step(int firstStage, int lastStage, boolean lethal) {
        static final Step NONE = new Step(1, 0, false);
        static final Step LETHAL = new Step(1, 0, true);

        boolean hasStages() {
            return !lethal && lastStage >= firstStage;
        }
    }
}
