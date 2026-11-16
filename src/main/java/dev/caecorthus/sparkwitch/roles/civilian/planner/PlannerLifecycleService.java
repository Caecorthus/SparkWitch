package dev.caecorthus.sparkwitch.roles.civilian.planner;

import dev.caecorthus.sparkwitch.mixin.PlayerMoodComponentAccessor;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * The planner's world tick (END_WORLD_TICK, after every player's Wathe mood tick of that world tick):
 * <ul>
 *   <li>Recommendation markers on any player ({@link #reconcile}): a marked task completed, rerolled, replaced or
 *   removed elsewhere, or a map a Bell Ringer Echo holds, only loses its marker; a recommendation that would be expired
 *   on the next mood tick, or one on a player who is no longer a living round participant, is dropped silently
 *   together with its task (no pay, no {@code TaskComplete}).</li>
 *   <li>Next-task passive: for a living planner in an ACTIVE round, the seconds left on Wathe's private
 *   {@code nextTaskTimer} (read through {@link PlayerMoodComponentAccessor}, never written) at the timer's observed
 *   speed, or -1 when no deal can happen; published to the owner only when the shown second changes; -1 for everyone
 *   else.</li>
 * </ul>
 * A player with no marker, no planner role and nothing published costs one component lookup and one role lookup.
 * 规划大师的世界刻（END_WORLD_TICK，位于该世界刻内每名玩家的 Wathe 情绪刻之后）：
 * 任何玩家身上的推荐标记（{@link #reconcile}）：被标记的任务已完成、被重排、被替换或在别处被移除，或任务表被敲钟人回响占用时，
 * 只清除标记；在下一次情绪刻时将会过期的推荐，或其持有者不再是存活的局内参与者时，连同任务一起静默清除（不结算、不触发
 * {@code TaskComplete}）。
 * 下一任务被动：ACTIVE 回合中存活的规划大师，按观测到的计时速度发布 Wathe 私有 {@code nextTaskTimer} 的剩余秒数（经
 * {@link PlayerMoodComponentAccessor} 只读，从不写入），无法发任务时为 -1；仅在显示的秒数变化时同步给本人；其他人为 -1。
 * 没有标记、不是规划大师且没有已发布数值的玩家只花一次组件查询与一次职业查询。
 */
final class PlannerLifecycleService {
    private PlannerLifecycleService() {
    }

    static void register() {
        ServerTickEvents.END_WORLD_TICK.register(PlannerLifecycleService::tick);
    }

    private static void tick(ServerWorld world) {
        long now = world.getTime();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        for (ServerPlayerEntity player : world.getPlayers()) {
            PlannerComponent state = PlannerComponent.KEY.get(player);
            boolean planner = PlannerRules.isPlanner(game.getRole(player));
            if (!planner && !state.hasRecommendation() && state.nextTaskSeconds() == -1) {
                continue;
            }
            boolean participant = isParticipant(player, game);
            if (state.hasRecommendation()) {
                reconcile(player, now, participant);
            }
            if (planner && participant) {
                publishNextTask(player, state);
            } else {
                state.forgetNextTaskTimer();
                state.setNextTaskSeconds(-1);
            }
        }
    }

    /**
     * Seconds until Wathe deals the planner's next task, at the timer's observed speed (SparkTraits Anxiety and the
     * like speed it up), or "unknown" (-1) when no deal can happen: every type is held or a Bell Ringer Echo holds
     * generation. Read-only: Wathe's timer is never written.
     * 距 Wathe 给规划大师发下一个任务的秒数，按观测到的计时速度换算（SparkTraits 焦虑等会加快它）；无法发任务时为“未知”（-1）：
     * 所有类型都已持有，或敲钟人回响暂停了生成。只读：从不写入 Wathe 的计时。
     */
    private static void publishNextTask(ServerPlayerEntity player, PlannerComponent state) {
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
        int timer = ((PlayerMoodComponentAccessor) mood).sparkwitch$getNextTaskTimer();
        float rate = state.observeNextTaskTimer(timer);
        state.setNextTaskSeconds(PlannerRules.nextTaskSeconds(timer, rate,
                PlannerRules.canDealNextTask(mood.tasks.size(), PlannerTasks.isLocked(player))));
    }

    /** ACTIVE round, playing and alive (an alive spectator in a Rift Gate stays a participant). / ACTIVE 回合中参与且存活。 */
    static boolean isParticipant(PlayerEntity player, GameWorldComponent game) {
        return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && GameFunctions.isPlayerPlayingAndAlive(player);
    }

    /** Settles {@code player}'s marker against their task map ({@link PlannerRules#markerAction}). / 按任务表结算标记。 */
    static void reconcile(ServerPlayerEntity player, long now, boolean participant) {
        PlannerComponent state = PlannerComponent.KEY.get(player);
        PlayerMoodComponent.Task type = state.recommendedType();
        if (type == null) {
            return;
        }
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
        // The exact inserted task object, so a same-type replacement (Bell Ringer Echo, slip) only clears the marker;
        // a held map (Bell Ringer Echo) only clears it too. Expiry is judged for the next mood tick, so an expired
        // recommendation never completes as an ordinary task on its end tick.
        // 按插入的那个任务对象判断，因此同类型的替换（敲钟人回响、任务单）只清除标记；任务表被占用（敲钟人回响）时同样只清除。
        // 过期按下一次情绪刻判断，因此过期的推荐不会在结束刻作为普通任务完成。
        switch (PlannerRules.markerAction(participant, PlannerTasks.isLocked(player),
                state.holdsRecommendation(mood.tasks),
                PlannerRules.expiresBeforeNextMoodTick(now, state.recommendationEndTick()))) {
            case KEEP -> {
            }
            case CLEAR -> state.clearRecommendation();
            case DROP -> PlannerTasks.dropRecommendation(player);
        }
    }

    /**
     * Role assignment (the deal, or a mid-round role change of this player) and Wathe's {@code ResetPlayer}: the
     * marked task is dropped with its marker, then everything else is cleared.
     * 身份分配（发牌，或该玩家局中换职业）与 Wathe 的 {@code ResetPlayer}：被标记的任务连同标记一起移除，然后清空其余状态。
     */
    static void clear(ServerPlayerEntity player) {
        PlannerTasks.dropRecommendation(player);
        PlannerComponent.KEY.get(player).clear();
    }
}
