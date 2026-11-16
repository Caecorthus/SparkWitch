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

import java.util.List;

/**
 * Server side of the task slip ({@code sparkwitch:planner_task_slip}). Whoever holds it may use it (no role claim, so
 * the target's line names the user and never says "planner"): the living participant under the user's crosshair within
 * {@link PlannerRules#REACH} (walls block the ray), or the user when nobody is aimed at, has every task replaced by the
 * same number of distinct random native types and loses any recommendation marker. A target with no task to reroll
 * (none at all, or a Bell Ringer Echo holding the map) refuses with a line and the slip is kept.
 * 任务单（{@code sparkwitch:planner_task_slip}）的服务端部分。谁拿到都能用（不声明身份，因此给目标的提示写的是使用者名字，
 * 从不提“规划大师”）：用户准星下 {@link PlannerRules#REACH} 格内的存活参与者（墙会挡住射线），或无目标时用户自己，全部任务
 * 被替换为同样数量、互不相同的随机原生类型，并失去推荐标记。没有可重排任务的目标（一个都没有，或任务表被敲钟人回响占用）会
 * 提示拒绝，任务单保留。
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
                mood.tasks.size(),
                PlannerTasks.isLocked(target));
        switch (verdict) {
            case USE -> {
                List<PlayerMoodComponent.Task> types = PlannerRules.rerollTypes(mood.tasks.size(),
                        bound -> user.getRandom().nextInt(bound));
                PlannerTasks.replaceAll(target, types);
                user.playSoundToPlayer(SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.8F, 0.9F);
                if (self) {
                    user.sendMessage(Text.translatable("message.sparkwitch.planner.task_slip.self")
                            .formatted(Formatting.GOLD), true);
                } else {
                    // Never names the target to the user (a player hidden in BEAR smoke is still hit by the ray).
                    // 从不向使用者写出目标名字（藏在 BEAR 烟雾中的玩家同样会被射线命中）。
                    user.sendMessage(Text.translatable("message.sparkwitch.planner.task_slip.given")
                            .formatted(Formatting.GOLD), true);
                    target.playSoundToPlayer(SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.6F, 0.9F);
                    target.sendMessage(Text.translatable("message.sparkwitch.planner.task_slip", user.getName())
                            .formatted(Formatting.GOLD), true);
                }
                PlannerReplay.recordTaskSlip(user, self ? null : target);
                return true;
            }
            case NO_TASKS -> user.sendMessage(Text.translatable(self
                    ? "message.sparkwitch.planner.task_slip.no_tasks.self"
                    : "message.sparkwitch.planner.task_slip.no_tasks")
                    .formatted(Formatting.RED), true);
            case REFUSE -> {
            }
        }
        return false;
    }
}
