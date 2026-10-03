package dev.caecorthus.sparkwitch.roles.civilian.saint;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps Saint's good-role coin economy scoped to that exact role.
 * 将圣徒的好人金币初始化、任务收益与可见性严格限制在该职业内。
 */
public final class SaintEconomyService {
    public static final int INITIAL_MONEY = 0;
    public static final int TASK_MONEY_REWARD = 50;
    private static boolean registered;

    private SaintEconomyService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        CanSeeMoney.EVENT.register(SaintEconomyService::canSeeMoney);
    }

    public static void assignForRole(ServerPlayerEntity player, Role role) {
        if (shouldInitializeMoney(role)) {
            PlayerShopComponent.KEY.get(player).setBalance(INITIAL_MONEY);
        }
    }

    public static void onTaskComplete(ServerPlayerEntity player) {
        Role role = GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player);
        if (earnsTaskMoney(role)) {
            PlayerShopComponent.KEY.get(player).addToBalance(TASK_MONEY_REWARD);
        }
    }

    static boolean shouldInitializeMoney(@Nullable Role role) {
        return SaintRules.isSaint(role);
    }

    static boolean earnsTaskMoney(@Nullable Role role) {
        return SaintRules.isSaint(role);
    }

    static CanSeeMoney.Result moneyVisibilityResult(@Nullable Role role) {
        return SaintRules.isSaint(role) ? CanSeeMoney.Result.ALLOW : null;
    }

    /**
     * Outside a running game or once dead, the Saint answers DENY instead of no answer. Since the Saint has a shop,
     * Wathe's fallback {@code ShopUtils.canAccessShop} is true for it, which would show coins to a dead Saint and
     * answer ALLOW while the round is STARTING (when SparkTraits rolls money-only traits). DENY keeps both answers
     * exactly as they were before the shop existed.
     * 不在进行中的对局或已死亡时，圣徒返回 DENY 而不是不作答。圣徒有了商店后，Wathe 的回退
     * {@code ShopUtils.canAccessShop} 对其为真，会让死亡的圣徒看到金币，并在 STARTING 阶段（SparkTraits 抽取金币词条时）
     * 返回 ALLOW。DENY 让这两种结果与商店出现之前完全一致。
     */
    static CanSeeMoney.Result moneyVisibility(@Nullable Role role, boolean playingAndAlive) {
        if (!playingAndAlive) {
            return SaintRules.isSaint(role) ? CanSeeMoney.Result.DENY : null;
        }
        return moneyVisibilityResult(role);
    }

    private static CanSeeMoney.Result canSeeMoney(PlayerEntity player) {
        if (player == null) {
            return null;
        }
        return moneyVisibility(GameWorldComponent.KEY.get(player.getWorld()).getRole(player),
                GameFunctions.isPlayerPlayingAndAlive(player));
    }
}
