package dev.caecorthus.sparkwitch.roles.neutral.koko;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanWinService;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendWinService;
import dev.doctor4t.wathe.api.event.CheckWinCondition;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Stable contract: Koko's win listener runs in {@link #PHASE} on Wathe's first-non-null {@code CheckWinCondition},
 * after the Fiend Moment and the Charon's Soul Moment (which suspend every other win while they run) and before the
 * default phase, so a default-phase {@code block()} (the Murderous Witch, the witch faction) cannot hide her win.
 * She wins alone the moment she is alive, not swallowed or inside a Rift Gate, live and holding 1500 traffic
 * ({@link KokoRules#wins}). Otherwise it answers {@code null}: she blocks nobody, so killers and passengers settle
 * around her as around any other living neutral, and she loses with them if the round ends first.
 * 稳定契约：可可的胜负监听器运行于 {@link #PHASE}，在 Wathe「首个非 null 生效」的 {@code CheckWinCondition} 上排在魔人时刻与
 * 卡戎亡魂时刻之后（它们进行期间暂停其他一切胜利）、默认阶段之前，因此默认阶段的 {@code block()}（杀意魔女、魔女阵营）无法
 * 盖住她的胜利。只要她存活、未被吞下也不在裂隙门内、正在直播且流量达到 1500（{@link KokoRules#wins}），她立即独自获胜。
 * 其余情况返回 {@code null}：她不阻止任何人，杀手与乘客照常围绕她结算，与其他存活的中立职业一样；若对局先结束，她就输了。
 */
public final class KokoWinService {
    public static final Identifier PHASE = SparkWitch.id("koko_stream_win");
    private static boolean registered;

    private KokoWinService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        CheckWinCondition.EVENT.addPhaseOrdering(FiendWinService.PHASE, PHASE);
        CheckWinCondition.EVENT.addPhaseOrdering(FerrymanWinService.PHASE, PHASE);
        CheckWinCondition.EVENT.addPhaseOrdering(PHASE, Event.DEFAULT_PHASE);
        CheckWinCondition.EVENT.register(PHASE, KokoWinService::checkWin);
    }

    private static CheckWinCondition.@Nullable WinResult checkWin(ServerWorld world, GameWorldComponent game,
                                                                 GameFunctions.WinStatus currentStatus) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            // The component read comes first: it is empty for everyone who is not a Koko with traffic.
            // 先读组件：除了已有流量的可可，其他人的组件都是空的。
            KokoComponent state = KokoComponent.KEY.get(player);
            if (state.traffic() < KokoRules.WIN_TRAFFIC) {
                continue;
            }
            // An alive spectator (a Rift Gate occupant) counts like a swallowed player: no win from in there.
            // 存活的旁观者（裂隙门内）与被吞下的玩家同样处理：在里面不能获胜。
            if (KokoRules.wins(KokoRules.isKoko(game.getRole(player)), GameFunctions.isPlayerPlayingAndAlive(player),
                    player.isSpectator() || NoellesTaotieSeekerBridge.isSwallowed(player), state.isLive(),
                    state.traffic())) {
                return CheckWinCondition.WinResult.neutralWin(player);
            }
        }
        return null;
    }
}
