package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.event.CheckWinCondition;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
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
            // The Charon is gone: the Lost Souls die with him before normal resolution resumes.
            // 卡戎已不在：迷失的灵魂随他一同消亡，然后恢复常规结算。
            FerrymanMomentService.endWithSouls(world, moment);
            return null;
        }
        if (moment.soulCount() == 0) {
            return null;
        }
        if (FerrymanMomentRules.outsidersAlive(world, moment) != 0) {
            return CheckWinCondition.WinResult.block();
        }
        // The Charon never wins alone: every Lost Soul still standing is a co-winner, so the round resolves for the
        // whole moment instead of crediting only its owner (owner report: the souls were not winning with him).
        // 卡戎从不独自获胜：每一名仍存活的迷失灵魂都是并列胜利者，结算按整个时刻算，而不是只记在拥有者名下。
        List<ServerPlayerEntity> coWinners = new ArrayList<>();
        for (UUID soulId : moment.souls()) {
            ServerPlayerEntity soul = world.getServer().getPlayerManager().getPlayer(soulId);
            if (soul != null && GameFunctions.isPlayerPlayingAndAlive(soul)) {
                coWinners.add(soul);
            }
        }
        return CheckWinCondition.WinResult.neutralWin(owner, coWinners);
    }
}
