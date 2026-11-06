package dev.caecorthus.sparkwitch.roles.civilian.savior;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.event.CheckWinCondition;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

/**
 * A living Savior always keeps the passenger side alive for the killer-win check. Wathe's base check reads
 * {@code GameWorldComponent.isInnocent}, which SparkTraits may flip through alignment traits (for example the hidden
 * Jester); the Savior's own role must still count, so the killer win is blocked until the Savior dies or the round
 * times out. Only the KILLERS status is replaced; the passengers/time statuses are left to Wathe.
 * 存活救世主始终让好人阵营在杀手胜利判定中保持存活。Wathe 基础判定读取的 isInnocent 可能被 SparkTraits 的
 * 阵营天赋（例如隐藏小丑）翻转；救世主身份本身必须计入，因此在救世主死亡或回合超时前阻止杀手胜利。
 * 只替换 KILLERS：乘客/超时结算仍交给 Wathe。
 */
public final class SaviorWinService {
    /** Runs before SparkTraits' default-phase team-win census, which can still read the Savior as a non-civilian.
     *  先于 SparkTraits 默认阶段的队伍胜利统计运行，避免救世主仍被算作非好人。 */
    public static final Identifier PHASE = SparkWitch.id("savior_win");
    private static boolean registered;

    private SaviorWinService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        CheckWinCondition.EVENT.addPhaseOrdering(PHASE, Event.DEFAULT_PHASE);
        CheckWinCondition.EVENT.register(PHASE, SaviorWinService::checkWin);
    }

    private static CheckWinCondition.WinResult checkWin(
            ServerWorld world,
            GameWorldComponent game,
            GameFunctions.WinStatus currentStatus
    ) {
        if (currentStatus != GameFunctions.WinStatus.KILLERS || !hasLivingSavior(world, game)) {
            return null;
        }
        return CheckWinCondition.WinResult.block();
    }

    static boolean hasLivingSavior(ServerWorld world, GameWorldComponent game) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (SaviorRules.isSavior(game.getRole(player)) && GameFunctions.isPlayerPlayingAndAlive(player)) {
                return true;
            }
        }
        return false;
    }
}
