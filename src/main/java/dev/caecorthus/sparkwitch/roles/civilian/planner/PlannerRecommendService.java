package dev.caecorthus.sparkwitch.roles.civilian.planner;

import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Server side of the recommendation ({@code sparkwitch:use_planner_recommend}). The client names the player under its
 * crosshair (its own UUID when nothing is aimed at); that UUID is only a hint. {@link #tryRecommend} re-checks the
 * planner's role, round, control state, readiness and Fear, then the target (found in the planner's world, targetable,
 * within reach and in sight), the coins, the target's live recommendation and the target's free task types
 * ({@link PlannerRules#recommendVerdict}). Only {@code USE} changes anything: one native Wathe task of a type the
 * target does not hold, the marker, the 25 coins, the cooldown and the replay line.
 * 推荐（{@code sparkwitch:use_planner_recommend}）的服务端部分。客户端只指明准星下的玩家（无目标时为自己的 UUID），该 UUID
 * 只是提示。{@link #tryRecommend} 重新校验规划大师的职业、回合、受控状态、就绪与恐惧，再校验目标（在规划大师所在世界中找到、
 * 可被选中、距离内且在视线内）、金币、目标的有效推荐与目标的空闲任务类型（{@link PlannerRules#recommendVerdict}）。只有
 * {@code USE} 会改变状态：一项目标尚未持有类型的 Wathe 原生任务、标记、25 金币、冷却与回放。
 */
public final class PlannerRecommendService {
    private PlannerRecommendService() {
    }

    /**
     * Called on the server thread for every request. The payload policy guards (stun, Seeker session, Rift Gate, Fear,
     * Knight duel, Taotie daze, opening safe time) already drop most refused requests; this re-checks every gate.
     * 每个请求都在服务端线程调用。数据包策略拦截（眩晕、搜寻者会话、裂隙门、恐惧、骑士决斗、饕餮眩晕、开局安全时间）已丢弃
     * 大多数被拒请求；此处重新检查每个门槛。
     */
    public static void tryRecommend(ServerPlayerEntity planner, @Nullable UUID targetId) {
        if (planner == null || targetId == null) {
            return;
        }
        ServerWorld world = planner.getServerWorld();
        long now = world.getTime();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        PlannerComponent state = PlannerComponent.KEY.get(planner);
        boolean self = planner.getUuid().equals(targetId);
        PlayerEntity found = self ? planner : world.getPlayerByUuid(targetId);
        ServerPlayerEntity target = found instanceof ServerPlayerEntity serverTarget ? serverTarget : null;
        if (target != null) {
            // A marker that ran out or lost its task this tick is settled before it can block or be overwritten.
            // 本刻已过期或已失去任务的标记先结算，避免它挡住新推荐或被直接覆盖。
            PlannerLifecycleService.reconcile(target, now, PlannerLifecycleService.isParticipant(target, game));
        }
        PlannerComponent targetState = target == null ? null : PlannerComponent.KEY.get(target);
        PlayerMoodComponent mood = target == null ? null : PlayerMoodComponent.KEY.get(target);
        List<PlayerMoodComponent.Task> free = mood == null ? List.of() : PlannerRules.absentTypes(mood.tasks.keySet());
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(planner);
        PlannerRules.RecommendVerdict verdict = PlannerRules.recommendVerdict(
                PlannerRules.isPlanner(game.getRole(planner)),
                game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(planner) && !planner.isSpectator(),
                isControlled(planner),
                state.skillCooldownRemaining(now) <= 0,
                GrandWitchFearService.isPlayerFeared(planner),
                PlannerRules.validTarget(self, target != null,
                        target != null && PlannerTargeting.isTargetable(target),
                        target != null && PlannerTargeting.inReach(planner, target),
                        target != null && PlannerTargeting.inSight(planner, target)),
                shop.getBalance() >= PlannerRules.SKILL_COST,
                targetState != null && targetState.hasRecommendation(),
                target != null && !free.isEmpty() && !PlannerTasks.isLocked(target));
        switch (verdict) {
            case USE -> recommend(planner, target, self, free, shop, state, now);
            case FEARED -> GrandWitchFearService.sendSkillBlocked(planner);
            case NO_TARGET -> tell(planner, Text.translatable("message.sparkwitch.planner.no_target"));
            case NO_COINS -> tell(planner, Text.translatable("message.sparkwitch.planner.no_coins",
                    PlannerRules.SKILL_COST));
            // Feedback never names the target: the client also sends the UUID of a player hidden in BEAR smoke, so a
            // name here would reveal who is inside. / 提示从不写出目标名字：客户端对藏在 BEAR 烟雾中的玩家同样会发送
            // UUID，在这里写出名字会暴露烟雾里是谁。
            case TARGET_BUSY -> tell(planner, Text.translatable(self
                    ? "message.sparkwitch.planner.target_busy.self" : "message.sparkwitch.planner.target_busy"));
            case TARGET_FULL -> tell(planner, Text.translatable(self
                    ? "message.sparkwitch.planner.target_full.self" : "message.sparkwitch.planner.target_full"));
            case REFUSE -> {
            }
        }
    }

    private static void recommend(ServerPlayerEntity planner, ServerPlayerEntity target, boolean self,
                                  List<PlayerMoodComponent.Task> free, PlayerShopComponent shop,
                                  PlannerComponent state, long now) {
        PlayerMoodComponent.Task type = PlannerRules.pickType(free, bound -> planner.getRandom().nextInt(bound));
        if (type == null) {
            return;
        }
        PlannerTasks.addRecommendation(target, type, now + PlannerRules.RECOMMENDATION_LIFETIME_TICKS);
        // addToBalance with a negative amount, the house way for skill costs (Medium, Sherry, Prophet).
        // 以负数调用 addToBalance，与通灵师、橘雪莉、先知的技能扣费方式相同。
        shop.addToBalance(-PlannerRules.SKILL_COST);
        state.setSkillReadyTick(now + PlannerRules.COOLDOWN_TICKS);
        // Private cues: the recommendation is never announced to anyone else. / 私有提示音：推荐不会告知其他任何人。
        planner.playSoundToPlayer(SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.8F, 1.2F);
        if (!self) {
            target.playSoundToPlayer(SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.6F, 1.0F);
        }
        planner.sendMessage(Text.translatable(self
                ? "message.sparkwitch.planner.recommended.self" : "message.sparkwitch.planner.recommended")
                .formatted(Formatting.GOLD), true);
        PlannerReplay.recordRecommend(planner, self ? null : target);
    }

    /**
     * Swallowed by a Taotie, stunned by a Control Expert, or role-skill-blocked by SparkTraits (泽渡可可's set). The
     * other locks are payload guards that run before this code.
     * 被饕餮吞下、被控场专家眩晕，或被 SparkTraits 禁用职业技能（与泽渡可可相同）。其余锁定是先于本代码运行的数据包拦截。
     */
    static boolean isControlled(PlayerEntity player) {
        return NoellesTaotieSeekerBridge.isSwallowed(player)
                || ControlExpertStun.isStunned(player)
                || SparkTraitsKillerBridge.isRoleSkillBlocked(player);
    }

    private static void tell(ServerPlayerEntity player, Text message) {
        player.sendMessage(message.copy().formatted(Formatting.RED), true);
    }
}
