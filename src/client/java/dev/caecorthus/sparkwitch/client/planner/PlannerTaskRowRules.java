package dev.caecorthus.sparkwitch.client.planner;

import dev.doctor4t.wathe.cca.PlayerMoodComponent;

import java.util.Locale;

/**
 * Pure rules of the recommended task row in Wathe's top-left task list: which Wathe row is restyled and with which
 * translation key. The row is restyled only on a confirmed SparkWitch server, only for the task type the owner-only
 * {@code sparkwitch:planner_master_player} marker names, and never over a Bell Ringer Echo row: the Echo is a forced
 * task with a deadline and a penalty, so its red "must" line wins when both mark the same type. The four keys
 * {@code task.planner_recommend.<sleep|outside|eat|drink>} are the common lang contract.
 * Wathe 左上角任务列表中推荐任务行的纯规则：改写哪一行、使用哪个翻译键。只在已确认的 SparkWitch 服务器上、只针对仅本人同步的
 * {@code sparkwitch:planner_master_player} 标记所指的任务类型改写，且从不覆盖鸣钟人回响行：回响是带期限与惩罚的强制任务，
 * 两者标记同一类型时以其红色“必须”行为准。四个键 {@code task.planner_recommend.<sleep|outside|eat|drink>} 属于通用语言契约。
 */
public final class PlannerTaskRowRules {
    public static final String TASK_ROW_KEY_PREFIX = "task.planner_recommend.";

    private PlannerTaskRowRules() {
    }

    /** {@code task.planner_recommend.} plus the lower-case Wathe task name. / 前缀加小写的 Wathe 任务名。 */
    public static String taskRowKey(PlayerMoodComponent.Task type) {
        return TASK_ROW_KEY_PREFIX + type.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Whether this frame's row is restyled: a present task, on a confirmed server, marked as recommended, and not the
     * Echo task.
     * 本帧该行是否改写：任务存在、服务器已确认、被标记为推荐，且不是回响任务。
     */
    public static boolean restyles(boolean present, boolean confirmedServer, boolean recommended, boolean echoTask) {
        return present && confirmedServer && recommended && !echoTask;
    }
}
