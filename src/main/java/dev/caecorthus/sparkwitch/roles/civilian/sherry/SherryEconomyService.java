package dev.caecorthus.sparkwitch.roles.civilian.sherry;

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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 橘雪莉 coins and shop: +{@link SherryRules#PASSIVE_COINS} every {@link SherryRules#PASSIVE_INTERVAL_TICKS} ticks
 * (driven by her component's server tick) and +{@link SherryRules#TASK_COINS} per completed task, both through
 * {@code addToBalance} so income observers see ordinary income; the task pay is never given to a SparkTraits Impostor
 * (SparkTraits pays that itself, Medium/Accountant precedent). Her mana (natural regeneration and
 * +{@link SherryRules#TASK_MANA} per task) is owned by {@code WitchManaRules}, not here. The shop sells the detective
 * badge only; having an entry is also what lets her open the shop, and {@code CanSeeMoney} shows the money HUD.
 * 橘雪莉的金币与商店：每 {@link SherryRules#PASSIVE_INTERVAL_TICKS} 刻 +{@link SherryRules#PASSIVE_COINS}（由她的组件
 * 服务端 tick 驱动），每完成一个任务 +{@link SherryRules#TASK_COINS}，都经 {@code addToBalance} 发放，收入观察者视其为普通
 * 收入；SparkTraits 内鬼不发任务金币（由 SparkTraits 自行支付，通灵师/会计先例）。魔力（自然恢复与每任务
 * +{@link SherryRules#TASK_MANA}）由 {@code WitchManaRules} 负责，不在此处。商店只卖侦探徽章；有条目本身就让她能打开商店，
 * {@code CanSeeMoney} 则让金币 HUD 显示。
 */
final class SherryEconomyService {
    private static boolean registered;

    private SherryEconomyService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        TaskComplete.EVENT.register((player, task) -> onTaskComplete(player));
        CanSeeMoney.EVENT.register(SherryEconomyService::canSeeMoney);
        BuildShopEntries.EVENT.register(SherryEconomyService::buildEntries);
    }

    static void onTaskComplete(ServerPlayerEntity player) {
        if (canEarn(player) && SherryRules.receivesTaskMoney(SparkTraitsTaskIncomeBridge.isImpostor(player))) {
            PlayerShopComponent.KEY.get(player).addToBalance(SherryRules.TASK_COINS);
        }
    }

    /** Called by {@link SherryComponent#serverTick()} once per interval. / 由组件服务端 tick 每个周期调用一次。 */
    static void payPassiveIncome(ServerPlayerEntity player) {
        if (canEarn(player)) {
            PlayerShopComponent.KEY.get(player).addToBalance(SherryRules.PASSIVE_COINS);
        }
    }

    static boolean canEarn(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        Role role = game.getRole(player);
        // Cheap role test first: this runs on every task completion for every player. / 先做廉价的职业判断。
        if (!SherryRules.isSherry(role)) {
            return false;
        }
        return SherryRules.canEarn(game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(player), role, player.isSpectator(), player.isCreative(),
                SparkWitchApi.isWraithRestricted(player));
    }

    private static @Nullable CanSeeMoney.Result canSeeMoney(PlayerEntity player) {
        if (player == null || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return null;
        }
        return SherryRules.isSherry(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))
                ? CanSeeMoney.Result.ALLOW
                : null;
    }

    /**
     * Both sides, exact role only: Wathe builds the list on both sides and buys by index, so the gate ignores the
     * running state. The stock of {@link SherryRules#BADGE_STOCK} is Wathe's own per-round counter.
     * 双端、仅按确切职业：Wathe 在两端构建列表并按下标购买，因此门槛不看运行状态。{@link SherryRules#BADGE_STOCK} 的限购由
     * Wathe 自带的每局计数负责。
     */
    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (!SherryRules.isSherry(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return;
        }
        ShopEntry badge = new ShopEntry.Builder(SherryRules.BADGE_ENTRY_ID,
                new ItemStack(SparkWitchItems.sherryBadge()), SherryRules.BADGE_PRICE, ShopEntry.Type.TOOL)
                .stock(SherryRules.BADGE_STOCK)
                .build();
        context.addEntry(SparkTraitsCharismaBridge.discountShopEntry(player, badge));
    }
}
