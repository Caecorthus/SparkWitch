package dev.caecorthus.sparkwitch.roles.civilian.planner;

import dev.caecorthus.sparkwitch.roles.killer.bellringer.BellRingerEchoRuntime;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * The only writes the planner makes to Wathe's task map ({@code PlayerMoodComponent.tasks}), server thread only and
 * never from inside a {@code TaskComplete} listener (Wathe calls those while it iterates the map). Every task added is
 * a NATIVE Wathe task with Wathe's own durations, so eating, drinking, sleeping and fresh air fulfil it exactly as
 * they fulfil a dealt task, and its completion is an ordinary {@code TaskComplete} for every listener. A recommendation
 * is told apart only by the marker in {@link PlannerComponent}. {@code timesGotten} is left untouched, so Wathe's
 * later task odds do not change (the Bell Ringer Echo precedent).
 * 规划大师对 Wathe 任务表（{@code PlayerMoodComponent.tasks}）的全部写入，只在服务端线程，且从不在 {@code TaskComplete} 监听器
 * 内执行（Wathe 在遍历该表时调用这些监听器）。添加的都是使用 Wathe 自身时长的原生任务，因此吃、喝、睡与出去透气会像完成
 * 发放的任务一样完成它，完成时对所有监听器都是普通的 {@code TaskComplete}。推荐只靠 {@link PlannerComponent} 中的标记区分。
 * 不改动 {@code timesGotten}，因此 Wathe 之后的任务概率不变（沿用敲钟人回响的先例）。
 */
final class PlannerTasks {
    private PlannerTasks() {
    }

    static PlayerMoodComponent.TrainTask create(PlayerMoodComponent.Task type) {
        return switch (type) {
            case SLEEP -> new PlayerMoodComponent.SleepTask(GameConstants.SLEEP_TASK_DURATION);
            case OUTSIDE -> new PlayerMoodComponent.OutsideTask(GameConstants.OUTSIDE_TASK_DURATION);
            case EAT -> new PlayerMoodComponent.EatTask();
            case DRINK -> new PlayerMoodComponent.DrinkTask();
        };
    }

    /**
     * A Bell Ringer Echo holds the player's task map (its task must stay the only one), so neither a recommendation nor
     * a task slip may touch it.
     * 敲钟人回响占用玩家的任务表（其任务必须是唯一任务），因此推荐与任务单都不得改动它。
     */
    static boolean isLocked(PlayerEntity player) {
        return BellRingerEchoRuntime.holdsNormalTasks(player);
    }

    /**
     * Adds a recommended task: the marker (with the exact task object) is written before the mood sync, so the owner's
     * HUD never shows it as an ordinary task line (the Bell Ringer Echo order).
     * 添加推荐任务：标记（连同该任务对象）先于情绪同步写入，因此本人 HUD 不会把它显示成普通任务行（与敲钟人回响的顺序相同）。
     */
    static void addRecommendation(ServerPlayerEntity target, PlayerMoodComponent.Task type, long endTick) {
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(target);
        PlayerMoodComponent.TrainTask task = create(type);
        mood.tasks.put(type, task);
        PlannerComponent.KEY.get(target).setRecommendation(task, endTick);
        mood.sync();
    }

    /**
     * Silent drop: clears the marker and removes the task it marked, with no pay and no {@code TaskComplete}. A task of
     * the same type that replaced it (a Bell Ringer Echo task, a rerolled task) is not the recommendation and stays.
     * 静默清除：清除标记并移除其标记的任务，不结算，也不触发 {@code TaskComplete}。替换了它的同类型任务（敲钟人回响任务、
     * 重排后的任务）不是推荐，予以保留。
     */
    static void dropRecommendation(ServerPlayerEntity player) {
        PlannerComponent state = PlannerComponent.KEY.get(player);
        PlayerMoodComponent.Task type = state.recommendedType();
        if (type == null) {
            return;
        }
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
        boolean held = state.holdsRecommendation(mood.tasks);
        state.clearRecommendation();
        if (held && mood.tasks.remove(type) != null) {
            mood.sync();
        }
    }

    /**
     * Task slip: replaces every task with {@code types} (fresh native tasks, so sleep and fresh-air progress restarts)
     * and drops the recommendation marker, since the marked task is gone.
     * 任务单：用 {@code types} 替换全部任务（全新的原生任务，因此睡觉与透气的进度重新开始），并清除推荐标记，因为被标记的任务
     * 已不存在。
     */
    static void replaceAll(ServerPlayerEntity target, List<PlayerMoodComponent.Task> types) {
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(target);
        PlannerComponent.KEY.get(target).clearRecommendation();
        mood.tasks.clear();
        for (PlayerMoodComponent.Task type : types) {
            mood.tasks.put(type, create(type));
        }
        mood.sync();
    }
}
