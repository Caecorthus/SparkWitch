package dev.caecorthus.sparkwitch.compat.cooldown;

/**
 * Pure arithmetic and predicates behind the SparkWitch forced-cooldown stores; no game state.
 * SparkWitch 强制冷却存储背后的纯算术与判定，不读取任何游戏状态。
 */
public final class ForcedCooldownMath {
    private ForcedCooldownMath() {
    }

    /** {@code max(0, current) + ticks}, capped at {@link Integer#MAX_VALUE}. / 饱和加法，上限为 int 最大值。 */
    public static int saturatingAdd(int current, int ticks) {
        long sum = (long) Math.max(0, current) + Math.max(0, ticks);
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    /**
     * Ticks until the shared witch skill is usable again. A pending deferred cooldown starts only when the active
     * window ends, where the cooldown becomes {@code max(cooldown - window, deferred)}, so the total is
     * {@code max(cooldown, window + deferred)}.
     * 共享魔女技能再次可用前的 tick。待启动的延后冷却在主动窗口结束时才开始，届时冷却为
     * {@code max(cooldown - window, deferred)}，因此总时长为 {@code max(cooldown, window + deferred)}。
     */
    public static int witchSkillRemaining(int cooldownTicks, int deferredCooldownTicks, int activeWindowTicks) {
        int cooldown = Math.max(0, cooldownTicks);
        if (deferredCooldownTicks <= 0) {
            return cooldown;
        }
        return Math.max(cooldown, saturatingAdd(activeWindowTicks, deferredCooldownTicks));
    }

    /**
     * Floors for a raise: both the shared and a pending deferred cooldown are floored at {@code ticks}.
     * 抬高时的下限：共享冷却与待启动的延后冷却都以 {@code ticks} 为下限。
     */
    public static WitchSkillFloors raiseWitchSkill(int ticks) {
        int floor = Math.max(0, ticks);
        return new WitchSkillFloors(floor, floor);
    }

    /**
     * Floors for an extension: the shared cooldown grows by {@code ticks} (a ready skill starts a fresh cooldown), and
     * a pending deferred cooldown grows by the same amount so the extension survives the window end.
     * 延长时的下限：共享冷却增加 {@code ticks}（已就绪则从头冷却）；待启动的延后冷却同样增加，使延长在窗口结束后保留。
     */
    public static WitchSkillFloors extendWitchSkill(int cooldownTicks, int deferredCooldownTicks, int ticks) {
        return new WitchSkillFloors(
                saturatingAdd(cooldownTicks, ticks),
                deferredCooldownTicks > 0 ? saturatingAdd(deferredCooldownTicks, ticks) : 0
        );
    }

    /**
     * Mirror of {@code WitchPlayerComponent#raiseForcedCooldownFloors}: never shortens and never creates a deferred
     * cooldown.
     * 与 WitchPlayerComponent#raiseForcedCooldownFloors 一致：绝不缩短，也绝不新建延后冷却。
     */
    public static WitchSkillCounters applyWitchSkillFloors(
            int cooldownTicks,
            int deferredCooldownTicks,
            WitchSkillFloors floors
    ) {
        int cooldown = Math.max(cooldownTicks, floors.cooldownTicks());
        int deferred = deferredCooldownTicks > 0
                ? Math.max(deferredCooldownTicks, floors.deferredCooldownTicks())
                : deferredCooldownTicks;
        return new WitchSkillCounters(cooldown, deferred);
    }

    /**
     * A cooldown on the Kidnapper's drag skill also blocks the release press, so it is never forced while a body is
     * carried.
     * 绑架者拖尸技能的冷却也会拦住“放下”操作，因此携尸期间绝不强制冷却。
     */
    public static boolean mayForceWitchSkill(boolean kidnapperDragSkill, boolean carryingBody) {
        return !(kidnapperDragSkill && carryingBody);
    }

    /** Hellfire's end writes its own post cooldown, so a burning Hellfire is never forced. / 业火燃烧期间不强制。 */
    public static boolean mayForceSaintHellfire(boolean hellfireActive) {
        return !hellfireActive;
    }

    /** Absolute floors for the shared and deferred witch-skill counters. / 共享与延后魔女技能计数的绝对下限。 */
    public record WitchSkillFloors(int cooldownTicks, int deferredCooldownTicks) {
    }

    /** Resulting shared and deferred witch-skill counters. / 写入后的共享与延后魔女技能计数。 */
    public record WitchSkillCounters(int cooldownTicks, int deferredCooldownTicks) {
    }
}
