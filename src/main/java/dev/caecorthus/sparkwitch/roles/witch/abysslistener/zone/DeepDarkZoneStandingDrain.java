package dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone;

import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssListenerRules;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Owner D7 sanity drain while exposed on the Deep Dark Zone, split into the three operands of Wathe's per-tick drain
 * {@code if (!tasks.isEmpty()) setMood(mood - tasks.size() * MOOD_DRAIN)} so each one stays a natural value: the
 * pseudo task makes the task map count as non-empty and as one task more, and the ×15 scales the per-task rate. The
 * product is exactly {@link AbyssListenerRules#exposedDrainTasks(int)} × {@code MOOD_DRAIN}; it composes
 * multiplicatively with the Bell Ringer Echo ×1.5 (both scale {@code MOOD_DRAIN}) and still passes through the
 * SparkTraits and SparkStrength {@code setMood} argument adjusters. Both sides read the owner-synced exposure, so the
 * client prediction matches the server.
 * 所有者 D7：在深暗领域上暴露期间的理智下降。拆成 Wathe 每刻下降
 * {@code if (!tasks.isEmpty()) setMood(mood - tasks.size() * MOOD_DRAIN)} 的三个操作数，各自保持自然含义：临时任务使任务表
 * 视为非空并多计一个任务，×15 放大单任务速率。乘积恰为 {@link AbyssListenerRules#exposedDrainTasks(int)} × {@code MOOD_DRAIN}；
 * 与敲钟人回响 ×1.5 相乘叠加（二者都放大 {@code MOOD_DRAIN}），并仍经过 SparkTraits 与 SparkStrength 对 {@code setMood}
 * 参数的调整。双端都读取仅同步给本人的暴露标记，因此客户端预测与服务端一致。
 */
public final class DeepDarkZoneStandingDrain {
    private DeepDarkZoneStandingDrain() {
    }

    /** Both sides; false when the component is absent. / 双端；组件不存在时为 false。 */
    public static boolean isExposed(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        AbyssZoneExposureComponent exposure = AbyssZoneExposureComponent.KEY.getNullable(player);
        return exposure != null && exposure.isExposed();
    }

    /** Wathe's {@code tasks.isEmpty()} gate: the pseudo task keeps it draining. / 临时任务使下降不被跳过。 */
    public static boolean noDrainingTasks(boolean tasksEmpty, boolean exposed) {
        return tasksEmpty && !exposed;
    }

    /** Wathe's drain {@code tasks.size()}: the pseudo task counts as one more. / 临时任务多计一个任务。 */
    public static int drainingTaskCount(int realTasks, boolean exposed) {
        return exposed ? realTasks + AbyssListenerRules.ZONE_PSEUDO_TASKS : realTasks;
    }

    /** Wathe's {@code MOOD_DRAIN} (already scaled by earlier handlers): ×15 while exposed. / 暴露时 ×15。 */
    public static float drainPerTask(float drain, boolean exposed) {
        return exposed ? drain * AbyssListenerRules.ZONE_DRAIN_MULTIPLIER : drain;
    }

    /**
     * The per-tick drain this class produces for {@code realTasks} with Wathe's semantics (0 when Wathe would skip it).
     * Reference for tests and documentation only.
     * 按 Wathe 语义本类对 {@code realTasks} 产生的每刻下降量（Wathe 会跳过时为 0）。仅作测试与文档参考。
     */
    public static float perTickDrain(int realTasks, float moodDrain, boolean exposed) {
        if (noDrainingTasks(realTasks == 0, exposed)) {
            return 0.0F;
        }
        return drainingTaskCount(realTasks, exposed) * drainPerTask(moodDrain, exposed);
    }
}
