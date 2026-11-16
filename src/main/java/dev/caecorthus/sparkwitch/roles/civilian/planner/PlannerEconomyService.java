package dev.caecorthus.sparkwitch.roles.civilian.planner;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.api.SparkWitchApi;
import dev.caecorthus.sparkwitch.compat.SparkTraitsCharismaBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsTaskIncomeBridge;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/**
 * The planner's coins, the recommendation reward and the shop, all server authority:
 * <ul>
 *   <li>{@code TaskComplete} (one listener, so the order is fixed): first the planner's own
 *   +{@link PlannerRules#OWN_TASK_COINS} for every task (owner 2026-11-16, Q1), only for a living planner in an ACTIVE
 *   round and a confirmed non-Impostor (SparkTraits pays an Impostor's task income itself; owner rule from #189: no
 *   double pay); then, when the completed type is a live recommendation, the target's reward:
 *   {@link PlannerRules#rewardCoins} by the target's Wathe role faction and +{@link PlannerRules#REWARD_MOOD} sanity
 *   (Wathe clamps it; a FAKE-mood role stays at 1), and the marker is cleared. The task itself is an ordinary Wathe
 *   task, so Wathe's own +0.5 and every other listener already ran or still run normally. Nothing here touches the
 *   task map: Wathe calls this listener while iterating it.</li>
 *   <li>Shop: the task slip ({@link PlannerRules#SLIP_PRICE}, unlimited, re-buyable), a real item that any holder can
 *   use, passed through the optional SparkTraits Charisma discount; {@code CanSeeMoney} ALLOW for a living planner and
 *   DENY otherwise (Tono Hanna / Saint precedent).</li>
 * </ul>
 * All coins go through {@code addToBalance}, so income observers (SparkFactionAPI's income record, SparkTraits
 * Snowball, SparkStrength's purse) see ordinary income.
 * 规划大师的金币、推荐奖励与商店，全部由服务端决定：
 * {@code TaskComplete}（单个监听器，顺序固定）：先发规划大师自己每个任务 +{@link PlannerRules#OWN_TASK_COINS}（所有者
 * 2026-11-16，Q1），只发给 ACTIVE 回合中存活且确认不是内鬼的规划大师（内鬼的任务收入由 SparkTraits 自行支付；#189 的所有者
 * 规则：不重复发放）；再在完成的类型是有效推荐时发放目标奖励：按目标 Wathe 职业阵营的 {@link PlannerRules#rewardCoins} 与
 * +{@link PlannerRules#REWARD_MOOD} 理智（由 Wathe 截断；伪装理智职业保持 1），并清除标记。该任务本身就是普通 Wathe 任务，因此
 * Wathe 自己的 +0.5 与其他监听器照常执行。此处从不改动任务表：Wathe 在遍历任务表时调用本监听器。
 * 商店：任务单（{@link PlannerRules#SLIP_PRICE}，不限购，可重复购买），谁拿到都能用的真实物品，经过可选的 SparkTraits 魅力折扣；
 * {@code CanSeeMoney} 对存活的规划大师 ALLOW，否则 DENY（沿用远野汉娜与圣徒的先例）。
 * 所有金币都经 {@code addToBalance}，使收入观察者（SparkFactionAPI 收入记录、SparkTraits 滚雪球、SparkStrength 钱包）视其为普通收入。
 */
final class PlannerEconomyService {
    private PlannerEconomyService() {
    }

    static void register() {
        TaskComplete.EVENT.register(PlannerEconomyService::onTaskComplete);
        CanSeeMoney.EVENT.register(PlannerEconomyService::canSeeMoney);
        BuildShopEntries.EVENT.register(PlannerEconomyService::buildShop);
    }

    private static void onTaskComplete(ServerPlayerEntity serverPlayer, PlayerMoodComponent.Task type) {
        if (serverPlayer == null) {
            return;
        }
        PlannerComponent state = PlannerComponent.KEY.get(serverPlayer);
        // Wathe removes completed tasks only after this loop, so the map still holds the completing task object: a
        // same-type replacement (a Bell Ringer Echo task) never pays the reward. Reading the map is safe here.
        // Wathe 在循环结束后才移除已完成的任务，因此任务表中仍是正在完成的那个对象：同类型的替换（敲钟人回响任务）永远不会
        // 领到奖励。此处只读取任务表，是安全的。
        boolean recommended = state.isRecommendedTask(PlayerMoodComponent.KEY.get(serverPlayer).tasks.get(type))
                && !PlannerRules.isExpired(serverPlayer.getServerWorld().getTime(), state.recommendationEndTick());
        if (canEarn(serverPlayer) && Boolean.FALSE.equals(SparkTraitsTaskIncomeBridge.isImpostor(serverPlayer))) {
            int coins = PlannerRules.ownTaskCoins(recommended);
            if (coins > 0) {
                PlayerShopComponent.KEY.get(serverPlayer).addToBalance(coins);
            }
        }
        if (recommended) {
            reward(serverPlayer, state);
        }
    }

    private static void reward(ServerPlayerEntity target, PlannerComponent state) {
        Role role = GameWorldComponent.KEY.get(target.getWorld()).getRole(target);
        int coins = PlannerRules.rewardCoins(role == null ? null : role.getFaction());
        PlayerShopComponent.KEY.get(target).addToBalance(coins);
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(target);
        mood.setMood(mood.getMood() + PlannerRules.REWARD_MOOD);
        state.clearRecommendation();
        target.sendMessage(Text.translatable("message.sparkwitch.planner.reward", coins)
                .formatted(Formatting.GOLD), true);
    }

    /**
     * ACTIVE round, playing and alive, exactly the planner, survival, not a restricted Wraith (the Hanna gate).
     * 对局进行中、参与且存活、身份恰为规划大师、生存模式、不是受限冤魂（与汉娜相同）。
     */
    static boolean canEarn(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && PlannerRules.isPlanner(game.getRole(player))
                && !player.isSpectator() && !player.isCreative()
                && !SparkWitchApi.isWraithRestricted(player);
    }

    /**
     * Outside a running game or once dead, the planner answers DENY instead of no answer: the shop would otherwise
     * make Wathe's fallback show coins to a dead planner (Saint / Hanna precedent).
     * 不在进行中的对局或已死亡时返回 DENY 而不是不作答：否则商店会让 Wathe 的回退向死亡的规划大师显示金币（圣徒、汉娜先例）。
     */
    static @Nullable CanSeeMoney.Result moneyVisibility(@Nullable Role role, boolean playingAndAlive) {
        if (!PlannerRules.isPlanner(role)) {
            return null;
        }
        return playingAndAlive ? CanSeeMoney.Result.ALLOW : CanSeeMoney.Result.DENY;
    }

    private static @Nullable CanSeeMoney.Result canSeeMoney(PlayerEntity player) {
        if (player == null) {
            return null;
        }
        return moneyVisibility(GameWorldComponent.KEY.get(player.getWorld()).getRole(player),
                GameFunctions.isPlayerPlayingAndAlive(player));
    }

    /**
     * Built on both sides (Wathe builds the list on the client too and buys by index), so only synced state is read:
     * the role and the alive flag. Wathe's default purchase hands over a copy of the display stack, the real item.
     * 两端都会构建（Wathe 在客户端同样构建列表并按下标购买），因此只读取已同步的状态：职业与存活标记。Wathe 的默认购买会交付
     * 展示物品堆的副本，也就是真实物品。
     */
    private static void buildShop(PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (player == null || !PlannerRules.isPlanner(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))
                || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return;
        }
        ShopEntry slip = new ShopEntry.Builder(PlannerRules.TASK_SLIP_ENTRY,
                new ItemStack(SparkWitchItems.plannerTaskSlip()), PlannerRules.SLIP_PRICE, ShopEntry.Type.TOOL).build();
        context.addEntry(SparkTraitsCharismaBridge.discountShopEntry(player, slip));
    }
}
