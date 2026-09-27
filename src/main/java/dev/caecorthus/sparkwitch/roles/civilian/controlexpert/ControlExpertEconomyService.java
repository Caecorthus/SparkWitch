package dev.caecorthus.sparkwitch.roles.civilian.controlexpert;

import dev.caecorthus.sparkwitch.api.SparkWitchApi;
import dev.caecorthus.sparkwitch.compat.SparkTraitsControlExpertBridge;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Control Expert task economy on the Judge model: 0 at round start, +50 per completed task, nothing for a
 * SparkTraits Impostor (SparkTraits pays that trait's task income itself, so paying here would double it).
 * 控场专家沿用法官的任务经济：开局 0 金币，每完成一个任务 +50；SparkTraits 内鬼不在此发放
 * （该词条的任务收入由 SparkTraits 自行支付，此处再发会重复）。
 */
public final class ControlExpertEconomyService {
    private static final String SPARKTRAITS_MOD_ID = "sparktraits";
    private static boolean registered;

    private ControlExpertEconomyService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        TaskComplete.EVENT.register((player, task) -> onTaskComplete(player));
        CanSeeMoney.EVENT.register(ControlExpertEconomyService::canSeeMoney);
    }

    /** Round-start balance, written once the final role is known. / 开局余额，在最终身份确定后写入。 */
    static void initialize(ServerPlayerEntity player) {
        PlayerShopComponent.KEY.get(player).setBalance(ControlExpertRules.INITIAL_MONEY);
    }

    static void onTaskComplete(ServerPlayerEntity player) {
        if (canEarn(player) && receivesTaskMoney(FabricLoader.getInstance().isModLoaded(SPARKTRAITS_MOD_ID),
                SparkTraitsControlExpertBridge.isImpostor(player))) {
            PlayerShopComponent.KEY.get(player).addToBalance(ControlExpertRules.TASK_MONEY_REWARD);
        }
    }

    static boolean canEarn(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return canEarn(game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(player), game.getRole(player), player.isSpectator(),
                player.isCreative(), SparkWitchApi.isWraithRestricted(player));
    }

    /** Same eligibility as the Judge's task income. / 与法官任务收入相同的资格判定。 */
    static boolean canEarn(boolean active, boolean playingAndAlive, @Nullable Role role, boolean spectator,
                           boolean creative, boolean wraithRestricted) {
        return active && playingAndAlive && ControlExpertRules.isControlExpert(role)
                && !spectator && !creative && !wraithRestricted;
    }

    /**
     * SparkTraits absent → pay; present → pay only on a confirmed non-Impostor (unknown fails closed, Judge semantics).
     * 未安装 SparkTraits → 发放；已安装 → 仅在确认不是内鬼时发放（无法确认时不发放，与法官一致）。
     */
    static boolean receivesTaskMoney(boolean traitsLoaded, @Nullable Boolean impostor) {
        return !traitsLoaded || Boolean.FALSE.equals(impostor);
    }

    static @Nullable CanSeeMoney.Result moneyVisibilityResult(@Nullable Role role) {
        return ControlExpertRules.isControlExpert(role) ? CanSeeMoney.Result.ALLOW : null;
    }

    private static @Nullable CanSeeMoney.Result canSeeMoney(PlayerEntity player) {
        if (player == null || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return null;
        }
        return moneyVisibilityResult(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }
}
