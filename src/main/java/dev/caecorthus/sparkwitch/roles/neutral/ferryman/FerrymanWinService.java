package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.event.CheckWinCondition;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * The Soul Moment's win listener, ordered before the default phase so it decides before every faction win: while the
 * moment runs and outsiders remain, all other wins are blocked; when no outsider is left alive the Ferryman wins with
 * the Lost Souls. A moment whose owner died or lost the role ends here and normal resolution resumes.
 * 亡魂时刻的胜负监听器，排在默认阶段之前，因此先于所有阵营胜利作出决定：时刻进行且仍有外部存活者时，阻断其他一切胜利；
 * 外部存活者清零时，摆渡人与迷失的灵魂获胜。拥有者死亡或失去职业则在此结束时刻，恢复常规结算。
 */
public final class FerrymanWinService {
    public static final Identifier PHASE = SparkWitch.id("ferryman_moment_win");
    private static boolean registered;

    private FerrymanWinService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        CheckWinCondition.EVENT.addPhaseOrdering(PHASE, Event.DEFAULT_PHASE);
        CheckWinCondition.EVENT.register(PHASE, FerrymanWinService::checkWin);
    }

    private static CheckWinCondition.WinResult checkWin(ServerWorld world, GameWorldComponent game,
                                                        GameFunctions.WinStatus currentStatus) {
        FerrymanMomentWorldComponent moment = FerrymanMomentWorldComponent.get(world);
        if (!moment.isActive()) {
            return null;
        }
        UUID ownerId = moment.owner();
        ServerPlayerEntity owner = ownerId == null ? null : world.getServer().getPlayerManager().getPlayer(ownerId);
        if (owner == null || !FerrymanParticipation.isActiveFerryman(owner)) {
            FerrymanMomentService.end(world);
            return null;
        }
        if (moment.soulCount() == 0) {
            return null;
        }
        return FerrymanMomentRules.outsidersAlive(world, moment) == 0
                ? CheckWinCondition.WinResult.neutralWin(owner)
                : CheckWinCondition.WinResult.block();
    }
}
