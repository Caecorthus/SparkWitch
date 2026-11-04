package dev.caecorthus.sparkwitch.roles.civilian.noa;

import dev.caecorthus.sparkwitch.api.SparkWitchApi;
import dev.caecorthus.sparkwitch.compat.SparkTraitsNoaBridge;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.util.ShopEntry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Noa's coins and shop, all server authority, all gated to a living Noa in an ACTIVE round:
 * <ul>
 *   <li>Tasks: +{@link NoaRules#TASK_COINS}, only for a confirmed non-Impostor (SparkTraits pays an Impostor's task
 *   income itself; owner rule from #189: no double pay).</li>
 *   <li>Passive: +{@link NoaRules#PASSIVE_COINS} on Wathe's passive-money ticks (world time, every 10 s).</li>
 *   <li>Shop: two rows, 50 coins each, restoring or spending 25% sanity. A row at the end of the bar refuses and costs
 *   nothing. The shop and the balance are hidden once she is dead (Saint precedent).</li>
 * </ul>
 * 诺亚的金币与商店，全部由服务端决定，且只对 ACTIVE 回合中存活的诺亚生效：
 * 任务 +{@link NoaRules#TASK_COINS}，只发给确认不是内鬼的诺亚（内鬼的任务收入由 SparkTraits 自行支付；#189 的所有者规则：
 * 不重复发放）。被动：在 Wathe 被动金币刻（世界时间，每 10 秒）+{@link NoaRules#PASSIVE_COINS}。商店：两行，各 50 金币，
 * 回复或扣除 25% 理智；理智条已到该端点时该行拒绝且不扣钱。死亡后隐藏商店与余额（沿用圣徒的先例）。
 */
final class NoaEconomyService {
    static final String SANITY_UP_ENTRY = "sparkwitch:noa_sanity_up";
    static final String SANITY_DOWN_ENTRY = "sparkwitch:noa_sanity_down";

    private NoaEconomyService() {
    }

    static void register() {
        TaskComplete.EVENT.register((player, task) -> onTaskComplete(player));
        ServerTickEvents.END_WORLD_TICK.register(NoaEconomyService::tickPassiveIncome);
        CanSeeMoney.EVENT.register(NoaEconomyService::canSeeMoney);
        BuildShopEntries.EVENT.register(NoaEconomyService::buildShop);
    }

    private static void onTaskComplete(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity noa && canEarn(noa)
                && Boolean.FALSE.equals(SparkTraitsNoaBridge.isImpostor(noa))) {
            PlayerShopComponent.KEY.get(noa).addToBalance(NoaRules.TASK_COINS);
        }
    }

    /**
     * Same world tick as Wathe's killer ticker; {@code addToBalance} so income observers (SparkTraits Snowball) see it.
     * 与 Wathe 杀手计时器同一世界刻；使用 {@code addToBalance}，收入观察者（SparkTraits 滚雪球）视其为普通收入。
     */
    private static void tickPassiveIncome(ServerWorld world) {
        int income = NoaRules.passiveIncomeAt(world.getTime());
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
     * ACTIVE round, playing and alive, exactly Noa, survival, not a restricted Wraith (USEC eligibility).
     * 对局进行中、参与且存活、身份恰为诺亚、生存模式、不是受限冤魂（与 USEC 相同）。
     */
    static boolean canEarn(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && NoaRules.isNoa(game.getRole(player))
                && !player.isSpectator() && !player.isCreative()
                && !SparkWitchApi.isWraithRestricted(player);
    }

    /**
     * Outside a running game or once dead, Noa answers DENY instead of no answer: her shop would otherwise make
     * Wathe's fallback show coins to a dead Noa and answer ALLOW while the round is STARTING (Saint precedent).
     * 不在进行中的对局或已死亡时，诺亚返回 DENY 而不是不作答：否则她的商店会让 Wathe 的回退向死亡的诺亚显示金币，并在
     * STARTING 阶段返回 ALLOW（沿用圣徒的先例）。
     */
    static @Nullable CanSeeMoney.Result moneyVisibility(@Nullable Role role, boolean playingAndAlive) {
        if (!NoaRules.isNoa(role)) {
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

    private static void buildShop(PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (player == null || !NoaRules.isNoa(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))
                || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return;
        }
        context.addEntry(sanityRow(SANITY_UP_ENTRY, Items.LIGHT_BLUE_DYE, "shop.sparkwitch.noa.sanity_up", true));
        context.addEntry(sanityRow(SANITY_DOWN_ENTRY, Items.RED_DYE, "shop.sparkwitch.noa.sanity_down", false));
    }

    /**
     * Wathe runs {@code onBuy} before taking the coins, so returning false refuses the purchase for free.
     * Wathe 在扣钱之前执行 {@code onBuy}，因此返回 false 即免费拒绝购买。
     */
    private static ShopEntry sanityRow(String id, net.minecraft.item.Item icon, String nameKey, boolean restore) {
        ItemStack display = new ItemStack(icon);
        display.set(DataComponentTypes.CUSTOM_NAME, Text.translatable(nameKey));
        return new ShopEntry.Builder(id, display, NoaRules.SHOP_PRICE, ShopEntry.Type.TOOL)
                .onBuy(buyer -> {
                    if (!(buyer instanceof ServerPlayerEntity noa) || !canEarn(noa)) {
                        return false;
                    }
                    PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(noa);
                    Float next = NoaRules.shopSanity(mood.getMood(), restore);
                    if (next == null) {
                        return false;
                    }
                    mood.setMood(next);
                    return true;
                })
                .build();
    }
}
