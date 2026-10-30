package dev.caecorthus.sparkwitch.roles.civilian.knight;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.api.SparkWitchApi;
import dev.caecorthus.sparkwitch.compat.OptionalStaticMethod;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Knight economy on the Judge model: 0 at round start, +50 per completed task, money always visible.
 * 骑士沿用法官的任务经济：开局 0 金币，每完成一个任务 +50，金钱始终可见。
 */
public final class KnightEconomyService {
    private static final Identifier IMPOSTOR = Identifier.of("sparktraits", "impostor");
    private static final OptionalStaticMethod HAS_ACTIVE_TRAIT = OptionalStaticMethod.of("sparktraits",
                    "dev.caecorthus.sparktraits.api.SparkTraitsApi", "hasActiveTrait",
                    PlayerEntity.class, Identifier.class)
            .initializingClass()
            .warnOnce(error -> SparkWitch.LOGGER.warn(
                    "Knight task income cannot verify the optional Impostor trait; skipping the reward", error));
    private static boolean registered;

    private KnightEconomyService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                initializeBalances(serverWorld, game);
            }
        });
        TaskComplete.EVENT.register((player, task) -> onTaskComplete(player));
        CanSeeMoney.EVENT.register(KnightEconomyService::canSeeMoney);
    }

    /** Round-start balance on final roles; overwrites, never adds. / 最终身份上的开局余额；覆盖写入，不累加。 */
    static void initializeBalances(ServerWorld world, GameWorldComponent game) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (game.hasAnyRole(player) && !game.isPlayerDead(player.getUuid())
                    && KnightRules.isKnight(game.getRole(player))) {
                PlayerShopComponent.KEY.get(player).setBalance(KnightRules.INITIAL_MONEY);
            }
        }
    }

    static void onTaskComplete(ServerPlayerEntity player) {
        // Pays only on an exact FALSE: an absent SparkTraits counts as FALSE, an unknown answer never pays.
        // 仅在确切为 FALSE 时发放：未安装 SparkTraits 视为 FALSE，未知答案从不发放。
        if (canEarn(player) && Boolean.FALSE.equals(HAS_ACTIVE_TRAIT.callNullableBoolean(Boolean.FALSE, null, null,
                player, IMPOSTOR))) {
            PlayerShopComponent.KEY.get(player).addToBalance(KnightRules.TASK_MONEY_REWARD);
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

    /** Same eligibility as the Judge and the Control Expert. / 与法官、控场专家相同的资格判定。 */
    static boolean canEarn(boolean active, boolean playingAndAlive, @Nullable Role role, boolean spectator,
                           boolean creative, boolean wraithRestricted) {
        return active && playingAndAlive && KnightRules.isKnight(role)
                && !spectator && !creative && !wraithRestricted;
    }

    private static @Nullable CanSeeMoney.Result canSeeMoney(PlayerEntity player) {
        if (player == null || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return null;
        }
        return KnightRules.isKnight(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))
                ? CanSeeMoney.Result.ALLOW : null;
    }
}
