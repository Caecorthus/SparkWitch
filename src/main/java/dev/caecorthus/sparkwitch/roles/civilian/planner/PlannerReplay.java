package dev.caecorthus.sparkwitch.roles.civilian.planner;

import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Replay for the planner: a recommendation is a skill event under {@code sparkwitch:planner_recommend}, a task slip an
 * item use under {@code sparkwitch:planner_task_slip} (recorded for whoever used it). The other player is the event's
 * target; a self-recommendation or a slip on oneself has none and gets its own {@code .self} line. Only keys are
 * stored, so every viewer reads the line in their own language.
 * 规划大师的回放：推荐是 {@code sparkwitch:planner_recommend} 下的技能事件，任务单是 {@code sparkwitch:planner_task_slip}
 * 下的物品使用（记录在实际使用者名下）。另一名玩家是事件目标；推荐给自己或对自己使用任务单时没有目标，使用单独的
 * {@code .self} 文案。只保存键名，因此每位观看者都以自己的语言阅读。
 */
final class PlannerReplay {
    static final String RECOMMEND_KEY = "replay.sparkwitch.planner_recommend";
    static final String TASK_SLIP_KEY = "replay.sparkwitch.planner_task_slip";
    private static boolean registered;

    private PlannerReplay() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerSkillFormatter(PlannerRules.RECOMMEND_SKILL_ID,
                (event, match, world) -> format(RECOMMEND_KEY, event, match));
        ReplayRegistry.registerItemUseFormatter(PlannerRules.TASK_SLIP_ID,
                (event, match, world) -> format(TASK_SLIP_KEY, event, match));
    }

    /** {@code target} null: the planner recommended a task to themselves. / {@code target} 为 null：推荐给了自己。 */
    static void recordRecommend(ServerPlayerEntity planner, @Nullable ServerPlayerEntity target) {
        GameRecordManager.recordSkillUse(planner, PlannerRules.RECOMMEND_SKILL_ID, target, null);
    }

    /** {@code target} null: the user rerolled their own tasks. / {@code target} 为 null：重排了自己的任务。 */
    static void recordTaskSlip(ServerPlayerEntity user, @Nullable ServerPlayerEntity target) {
        GameRecordManager.recordItemUse(user, PlannerRules.TASK_SLIP_ID, target, null);
    }

    private static @Nullable Text format(String key, GameRecordEvent event, GameRecordManager.MatchRecord match) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor")) {
            return null;
        }
        var players = ReplayGenerator.getPlayerInfoCache(match);
        Text actor = ReplayGenerator.formatPlayerName(data.getUuid("actor"), players);
        return data.containsUuid("target")
                ? Text.translatable(key, actor, ReplayGenerator.formatPlayerName(data.getUuid("target"), players))
                : Text.translatable(key + ".self", actor);
    }
}
