package dev.caecorthus.sparkwitch.roles.civilian.planner;

import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/**
 * Server side of the task slip ({@code sparkwitch:planner_task_slip}). Whoever holds it may use it (no role claim, and
 * the target's line names nobody): the visible living participant under the user's crosshair within
 * {@link PlannerRules#REACH} (walls block the ray), or the user when nobody is aimed at, has every task replaced by the
 * same number of distinct random native types and loses any recommendation marker. On another player the slip is
 * always handed over and used up with the normal lines, even when nothing can be rerolled (no task, or a Bell Ringer
 * Echo holding the map), so it never probes a role; only the user's own empty or held list refuses and keeps it.
 * 任务单（{@code sparkwitch:planner_task_slip}）的服务端部分。谁拿到都能用（不声明身份，给目标的提示也不写任何名字）：
 * 用户准星下 {@link PlannerRules#REACH} 格内可见的存活参与者（墙会挡住射线），或无目标时用户自己，全部任务
 * 被替换为同样数量、互不相同的随机原生类型，并失去推荐标记。对另一名玩家时，即使没有可重排的任务（一个都没有，或任务表被
 * 敲钟人回响占用），任务单也总会递出并用掉，提示与正常情况相同，因此从不用来探测身份；只有使用者自己的任务为空或被占用时才会
 * 提示拒绝并保留任务单。
 */
final class PlannerTaskSlipService {
    private PlannerTaskSlipService() {
    }

    /** A living, present participant of an ACTIVE round; safe on both sides. / ACTIVE 回合中在场的存活参与者；两端均可调用。 */
    static boolean inActiveRound(@Nullable PlayerEntity player) {
        return player != null
                && GameWorldComponent.KEY.get(player.getWorld()).getGameStatus()
                == GameWorldComponent.GameStatus.ACTIVE
                && GameFunctions.isPlayerPlayingAndAlive(player) && !player.isSpectator();
    }

    /** Server thread; true when the slip was used up. / 服务端线程；任务单被用掉时返回 true。 */
    static boolean use(ServerPlayerEntity user) {
        ServerPlayerEntity aimed = PlannerTargeting.findAimedPlayer(user);
        ServerPlayerEntity target = aimed != null ? aimed : user;
        boolean self = target == user;
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(target);
        PlannerRules.SlipVerdict verdict = PlannerRules.slipVerdict(
                GameWorldComponent.KEY.get(user.getWorld()).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(user) && !user.isSpectator(),
                NoellesTaotieSeekerBridge.isSwallowed(user) || ControlExpertStun.isStunned(user),
                self,
                mood.tasks.size(),
                PlannerTasks.isLocked(target));
        switch (verdict) {
            case USE, HAND_OVER -> {
                if (verdict == PlannerRules.SlipVerdict.USE) {
                    PlannerTasks.replaceAll(target, PlannerRules.rerollTypes(mood.tasks.size(),
                            bound -> user.getRandom().nextInt(bound)));
                }
                // HAND_OVER (another player with no task to reroll, or an Echo holding the list) reads exactly like a
                // reroll to both players, so the slip never tells anyone what the target is.
                // HAND_OVER（另一名没有可重排任务、或任务被回响占用的玩家）对双方看起来都与重排完全相同，因此任务单从不透露目标
                // 是什么身份。
                handOver(user, target, self);
                return true;
            }
            case NO_TASKS -> user.sendMessage(Text.translatable("message.sparkwitch.planner.task_slip.no_tasks.self")
                    .formatted(Formatting.RED), true);
            case REFUSE -> {
            }
        }
        return false;
    }

    /**
     * The lines, cues and replay of a used slip. Neither player's line names the other: the user may be a disguised
     * Morphling, invisible or hidden in BEAR smoke, and a smoke-hidden target is still hit by the ray. The replay keeps
     * both names (it is read after the round).
     * 用掉任务单后的提示、音效与回放。双方的提示都不写出对方的名字：使用者可能是伪装中的变形者、隐身或藏在 BEAR 烟雾中，而藏在
     * 烟雾中的目标同样会被射线命中。回放保留双方名字（在对局结束后阅读）。
     */
    private static void handOver(ServerPlayerEntity user, ServerPlayerEntity target, boolean self) {
        user.playSoundToPlayer(SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.8F, 0.9F);
        if (self) {
            user.sendMessage(Text.translatable("message.sparkwitch.planner.task_slip.self")
                    .formatted(Formatting.GOLD), true);
        } else {
            user.sendMessage(Text.translatable("message.sparkwitch.planner.task_slip.given")
                    .formatted(Formatting.GOLD), true);
            target.playSoundToPlayer(SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.6F, 0.9F);
            target.sendMessage(Text.translatable("message.sparkwitch.planner.task_slip")
                    .formatted(Formatting.GOLD), true);
        }
        PlannerReplay.recordTaskSlip(user, self ? null : target);
    }
}
