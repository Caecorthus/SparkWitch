package dev.caecorthus.sparkwitch.roles.civilian.planner;

import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of 规划大师: cooldown bookkeeping, the paid recommendation and its completion payout.
 * 规划大师的服务端：冷却记录、付费推荐与完成结算。
 */
public final class PlannerService {

    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();
    private static final PlayerMoodComponent.Task[] TYPES = PlayerMoodComponent.Task.values();

    private PlannerService() {
    }

    /** Builds a plain wathe task of the given type. 构造指定类型的原生任务。 */
    public static PlayerMoodComponent.TrainTask createNative(PlayerMoodComponent.Task type) {
        return switch (type) {
            case SLEEP -> new PlayerMoodComponent.SleepTask(dev.doctor4t.wathe.game.GameConstants.SLEEP_TASK_DURATION);
            case OUTSIDE -> new PlayerMoodComponent.OutsideTask(dev.doctor4t.wathe.game.GameConstants.OUTSIDE_TASK_DURATION);
            case EAT -> new PlayerMoodComponent.EatTask();
            case DRINK -> new PlayerMoodComponent.DrinkTask();
        };
    }

    /**
     * Shop tool effect: clears the caller's tasks and rerolls the same number of distinct types.
     * 商店道具效果：清空调用者的任务，并重刷同样数量的不同类型任务。
     */
    public static boolean reorderTasks(ServerPlayerEntity player) {
        if (player == null) {
            return false;
        }
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
        int count = mood.tasks.size();
        if (count <= 0) {
            return false;
        }
        mood.tasks.clear();
        dev.caecorthus.sparkwitch.component.PlannerPlayerComponent.KEY.get(player).clearRecommended();
        List<PlayerMoodComponent.Task> pool = new ArrayList<>(List.of(TYPES));
        int target = Math.min(count, pool.size());
        while (mood.tasks.size() < target) {
            PlayerMoodComponent.Task type = pool.remove(player.getRandom().nextInt(pool.size()));
            mood.tasks.put(type, createNative(type));
        }
        mood.sync();
        return true;
    }

    /**
     * Uses the slip on another player: their tasks are cleared and rerolled exactly like the self use, and they are
     * told who handed it to them.
     * 对另一名玩家使用任务单：与对自己使用相同，清空并重刷他的任务，同时告诉他是谁递的。
     */
    public static boolean handSlipTo(ServerPlayerEntity giver, ServerPlayerEntity target) {
        if (giver == null || target == null || giver == target) {
            return false;
        }
        if (!reorderTasks(target)) {
            return false;
        }
        dev.caecorthus.sparkwitch.roles.replay.SparkWitchReplay.recordSkill(
                giver, dev.caecorthus.sparkwitch.roles.replay.SparkWitchReplay.PLANNER_TASK_SLIP, target, null);
        target.sendMessage(net.minecraft.text.Text.translatable(
                "message.sparkwitch.planner.task_slip", giver.getDisplayName()), true);
        return true;
    }

    public static void register() {
        // The planner earns a flat bonus for every task they finish themselves.
        // 规划大师每完成一个自己的任务，额外获得固定金币。
        dev.doctor4t.wathe.api.event.TaskComplete.EVENT.register((player, taskType) -> {
            var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
            if (role != null && PlannerRules.ROLE_ID.equals(role.identifier())) {
                PlayerShopComponent.KEY.get(player).addToBalance(PlannerRules.OWN_TASK_REWARD);
            }
        });

        dev.doctor4t.wathe.api.event.BuildShopEntries.EVENT.register((player, context) -> {
            var role = dev.doctor4t.wathe.cca.GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
            if (role == null || !PlannerRules.ROLE_ID.equals(role.identifier())) {
                return;
            }
            context.getEntries().add(new dev.doctor4t.wathe.util.ShopEntry(
                    new net.minecraft.item.ItemStack(dev.caecorthus.sparkwitch.SparkWitchItems.plannerTaskSlip()),
                    PlannerRules.SLIP_PRICE,
                    dev.doctor4t.wathe.util.ShopEntry.Type.TOOL
            ));
        });
        RoleAssigned.EVENT.register((player, role) -> {
            if (role != null && PlannerRules.ROLE_ID.equals(role.identifier()) && player instanceof ServerPlayerEntity serverPlayer) {
                COOLDOWN_UNTIL.put(serverPlayer.getUuid(),
                        serverPlayer.getWorld().getTime() + PlannerRules.INITIAL_COOLDOWN_TICKS);
            }
        });
    }

    /** Handles one recommendation request from the planner's skill key. 处理规划大师技能键的一次推荐请求。 */
    public static void recommend(ServerPlayerEntity caster, UUID targetId) {
        if (caster == null || targetId == null || !(caster.getWorld() instanceof ServerWorld world)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        Role casterRole = game.getRole(caster);
        if (casterRole == null || !PlannerRules.ROLE_ID.equals(casterRole.identifier())) {
            return;
        }
        long now = world.getTime();
        if (now < COOLDOWN_UNTIL.getOrDefault(caster.getUuid(), 0L)) {
            return;
        }
        ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(targetId);
        if (target == null) {
            return;
        }
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(caster);
        if (shop.getBalance() < PlannerRules.SKILL_COST) {
            caster.sendMessage(net.minecraft.text.Text.translatable(
                    "message.sparkwitch.planner.not_enough_money", PlannerRules.SKILL_COST), true);
            return;
        }
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(target);
        // One recommendation at a time: overwriting would silently refund nothing and lose the old timer.
        // 同时只保留一条推荐：覆盖会静默丢弃旧计时器，且不返还金币。
        if (hasRecommendation(mood)) {
            COOLDOWN_UNTIL.put(caster.getUuid(), now + PlannerRules.COOLDOWN_TICKS);
            return;
        }
        PlayerMoodComponent.Task type = TYPES[caster.getRandom().nextInt(TYPES.length)];
        dev.caecorthus.sparkwitch.roles.replay.SparkWitchReplay.recordSkill(
                caster, dev.caecorthus.sparkwitch.roles.replay.SparkWitchReplay.PLANNER_RECOMMEND, target, null);
        mood.tasks.put(type, new PlannerRecommendTask(type));
        dev.caecorthus.sparkwitch.component.PlannerPlayerComponent.KEY.get(target).setRecommended(type, true);
        mood.sync();
        // Tell the target's client immediately which type is a recommendation; the component sync may arrive later.
        // 立刻告诉目标客户端哪个类型是推荐任务，不依赖组件同步的先后顺序。
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(target,
                new dev.caecorthus.sparkwitch.roles.civilian.planner.net.PlannerTaskAssignedS2CPayload(type.ordinal()));
        shop.addToBalance(-PlannerRules.SKILL_COST);
        COOLDOWN_UNTIL.put(caster.getUuid(), now + PlannerRules.COOLDOWN_TICKS);
    }

    /** Pays the completion reward for a finished recommendation. 为完成的推荐任务结算奖励。 */
    /** Clears the recommendation marker once the task is done or expired. 任务完成或过期后清除推荐标记。 */
    public static void clearRecommendation(ServerPlayerEntity player) {
        dev.caecorthus.sparkwitch.component.PlannerPlayerComponent.KEY.get(player)
                .clearRecommended();
    }

    public static void grantReward(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        Role role = game.getRole(player);
        boolean civilian = role != null && role.getFaction() == Faction.CIVILIAN;
        int coins = civilian ? PlannerRules.REWARD_COINS_CIVILIAN : PlannerRules.REWARD_COINS_OTHER;
        PlayerShopComponent.KEY.get(player).addToBalance(coins);
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
        mood.setMood(Math.min(1.0F, mood.getMood() + PlannerRules.REWARD_MOOD));
    }

    /** Number of recommendations currently on this player. 该玩家身上的推荐任务数量。 */
    public static int recommendationCount(PlayerMoodComponent mood) {
        int count = 0;
        for (PlayerMoodComponent.TrainTask task : mood.tasks.values()) {
            if (task instanceof PlannerRecommendTask) {
                count++;
            }
        }
        return count;
    }

    public static boolean hasRecommendation(PlayerMoodComponent mood) {
        return recommendationCount(mood) > 0;
    }

    public static boolean isOnCooldown(ServerPlayerEntity player) {
        return player != null
                && player.getWorld().getTime() < COOLDOWN_UNTIL.getOrDefault(player.getUuid(), 0L);
    }
}
