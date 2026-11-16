package dev.caecorthus.sparkwitch.roles.civilian.hanna;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.api.SparkWitchApi;
import dev.caecorthus.sparkwitch.compat.SparkTraitsCharismaBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsTaskIncomeBridge;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.util.ShopEntry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

/**
 * Hanna's coins and shop, all server authority, all gated to a living Hanna in an ACTIVE round:
 * <ul>
 *   <li>Tasks: +{@link HannaRules#TASK_COINS}, only for a confirmed non-Impostor (SparkTraits pays an Impostor's task
 *   income itself; owner rule from #189: no double pay). Her +{@link HannaRules#TASK_MANA} mana per task is not paid
 *   here: it is {@code WitchManaRules.taskReward}.</li>
 *   <li>Passive: +{@link HannaRules#PASSIVE_COINS} every {@link HannaRules#PASSIVE_INTERVAL_TICKS} of world time.</li>
 *   <li>Shop: the parasol and the handkerchief, real items that any holder can use, each passed through the optional
 *   SparkTraits Charisma discount like the Discoverer's row. The shop and the balance are hidden once she is dead
 *   (Saint and Noa precedent).</li>
 * </ul>
 * 汉娜的金币与商店，全部由服务端决定，且只对 ACTIVE 回合中存活的汉娜生效：
 * 任务 +{@link HannaRules#TASK_COINS}，只发给确认不是内鬼的汉娜（内鬼的任务收入由 SparkTraits 自行支付；#189 的所有者规则：
 * 不重复发放）。每个任务的 +{@link HannaRules#TASK_MANA} 魔力不在此处发放，而是 {@code WitchManaRules.taskReward}。
 * 被动：世界时间每 {@link HannaRules#PASSIVE_INTERVAL_TICKS} 刻 +{@link HannaRules#PASSIVE_COINS}。商店：洋伞与手帕，都是
 * 谁拿到都能用的真实物品，并像探索者的商品一样经过可选的 SparkTraits 魅力折扣。死亡后隐藏商店与余额（沿用圣徒与诺亚的先例）。
 */
final class HannaEconomyService {
    /** Stable shop entry ids (the item ids). / 稳定的商店条目 id（即物品 id）。 */
    static final String UMBRELLA_ENTRY = HannaRules.UMBRELLA_ID.toString();
    static final String HANDKERCHIEF_ENTRY = HannaRules.HANDKERCHIEF_ID.toString();

    private HannaEconomyService() {
    }

    static void register() {
        TaskComplete.EVENT.register((player, task) -> onTaskComplete(player));
        ServerTickEvents.END_WORLD_TICK.register(HannaEconomyService::tickPassiveIncome);
        CanSeeMoney.EVENT.register(HannaEconomyService::canSeeMoney);
        BuildShopEntries.EVENT.register(HannaEconomyService::buildShop);
    }

    /**
     * {@code addToBalance} so income observers (SparkFactionAPI's income record, SparkTraits Snowball, SparkStrength's
     * purse) see ordinary income. An unknown Impostor answer (null) never pays.
     * 使用 {@code addToBalance}，使收入观察者（SparkFactionAPI 收入记录、SparkTraits 滚雪球、SparkStrength 钱包）视其为普通
     * 收入。内鬼答案未知（null）时从不发放。
     */
    private static void onTaskComplete(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity hanna && canEarn(hanna)
                && Boolean.FALSE.equals(SparkTraitsTaskIncomeBridge.isImpostor(hanna))) {
            PlayerShopComponent.KEY.get(hanna).addToBalance(HannaRules.TASK_COINS);
        }
    }

    private static void tickPassiveIncome(ServerWorld world) {
        int income = HannaRules.passiveIncomeAt(world.getTime());
        if (income <= 0
                || GameWorldComponent.KEY.get(world).getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
            return;
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (canEarn(player)) {
                PlayerShopComponent.KEY.get(player).addToBalance(income);
            }
        }
    }

    /**
     * ACTIVE round, playing and alive, exactly Hanna, survival, not a restricted Wraith (the Noa and USEC gate).
     * 对局进行中、参与且存活、身份恰为汉娜、生存模式、不是受限冤魂（与诺亚、USEC 相同）。
     */
    static boolean canEarn(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && HannaRules.isHanna(game.getRole(player))
                && !player.isSpectator() && !player.isCreative()
                && !SparkWitchApi.isWraithRestricted(player);
    }

    /**
     * Outside a running game or once dead, Hanna answers DENY instead of no answer: her shop would otherwise make
     * Wathe's fallback show coins to a dead Hanna and answer ALLOW while the round is STARTING (Saint precedent).
     * 不在进行中的对局或已死亡时，汉娜返回 DENY 而不是不作答：否则她的商店会让 Wathe 的回退向死亡的汉娜显示金币，并在
     * STARTING 阶段返回 ALLOW（沿用圣徒的先例）。
     */
    static @Nullable CanSeeMoney.Result moneyVisibility(@Nullable Role role, boolean playingAndAlive) {
        if (!HannaRules.isHanna(role)) {
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
     * Built on both sides (Wathe builds the list on the client too), so only synced state is read: the role and the
     * alive flag. Wathe's default purchase hands over a copy of the display stack, which is the real item.
     * 两端都会构建（Wathe 在客户端同样构建列表），因此只读取已同步的状态：职业与存活标记。Wathe 的默认购买会交付展示物品堆的
     * 副本，也就是真实物品。
     */
    private static void buildShop(PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (player == null || !HannaRules.isHanna(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))
                || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return;
        }
        context.addEntry(row(player, UMBRELLA_ENTRY, SparkWitchItems.hannaUmbrella(), HannaRules.UMBRELLA_PRICE));
        context.addEntry(row(player, HANDKERCHIEF_ENTRY, SparkWitchItems.hannaHandkerchief(),
                HannaRules.HANDKERCHIEF_PRICE));
    }

    private static ShopEntry row(PlayerEntity player, String id, Item item, int price) {
        ShopEntry entry = new ShopEntry.Builder(id, new ItemStack(item), price, ShopEntry.Type.TOOL).build();
        return SparkTraitsCharismaBridge.discountShopEntry(player, entry);
    }
}
