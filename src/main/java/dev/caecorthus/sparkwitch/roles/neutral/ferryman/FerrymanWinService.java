package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendWinService;
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
 * The Soul Moment's win listener, ordered after the Fiend Moment's phase ({@link FiendWinService#PHASE}) and before the
 * default phase, so it decides before every faction win: while the moment runs and outsiders remain, every faction win
 * (KILLERS, PASSENGERS, ...) is blocked; when no outsider is left alive the Ferryman wins with the Lost Souls. The train
 * clock is the exception (owner 2026-11-09, "时间到照常结算"): when Wathe hands in {@code TIME} the moment abstains, so
 * the round ends on time as usual and the Charon and the souls simply do not win. A moment whose owner died or lost the
 * role ends here and normal resolution resumes.
 * 亡魂时刻的胜负监听器，排在魔人时刻阶段（{@link FiendWinService#PHASE}）之后、默认阶段之前，因此先于所有阵营胜利作出
 * 决定：时刻进行且仍有外部存活者时，阻断一切阵营胜利（KILLERS、PASSENGERS 等）；外部存活者清零时，摆渡人与迷失的灵魂
 * 获胜。列车时间是例外（所有者 2026-11-09：“时间到照常结算”）：Wathe 传入 {@code TIME} 时时刻弃权，对局照常按时间结束，
 * 摆渡人与灵魂只是不获胜。拥有者死亡或失去职业则在此结束时刻，恢复常规结算。
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
        // Stable phase order on Wathe's first-non-null CheckWinCondition: Fiend Moment, then Soul Moment, then the
        // default phase (every faction win). The Fiend Moment is short and timed, so its block or win settles first;
        // the Soul Moment still decides before every default listener. FerrymanMomentService's tick ends a moment whose
        // Charon is gone even on a tick the Fiend's block keeps this listener from running.
        // Wathe「首个非 null 生效」的 CheckWinCondition 上的稳定阶段顺序：魔人时刻、亡魂时刻、默认阶段（所有阵营胜利）。
        // 魔人时刻短且限时，因此它的阻断或胜利先结算；亡魂时刻仍先于所有默认阶段监听器作出决定。即使某个 tick 魔人的阻断让
        // 本监听器未能运行，FerrymanMomentService 的 tick 也会结束卡戎已不在的时刻。
        CheckWinCondition.EVENT.addPhaseOrdering(FiendWinService.PHASE, PHASE);
        CheckWinCondition.EVENT.addPhaseOrdering(PHASE, Event.DEFAULT_PHASE);
        CheckWinCondition.EVENT.register(PHASE, FerrymanWinService::checkWin);
    }

    /** What the listener answers for a running moment. / 时刻进行中时监听器的回答。 */
    enum Verdict {
        /** Owner gone: end the moment (souls die with him), then abstain. / 拥有者不在：结束时刻（灵魂随之而去）后弃权。 */
        END_AND_ABSTAIN,
        /** Let the other listeners and Wathe decide. / 交给其他监听器与 Wathe 决定。 */
        ABSTAIN,
        BLOCK,
        WIN
    }

    /**
     * Pure decision, in order: owner gone, no souls left, the train clock ran out (never blocked), outsiders left
     * (block), otherwise the moment wins. {@code TIME} comes before the win test as in Wathe, where an expired clock
     * outranks a same-tick KILLERS result.
     * 纯判定，依次为：拥有者不在、已无灵魂、列车时间耗尽（绝不阻断）、仍有外部者（阻断），否则时刻获胜。{@code TIME}
     * 排在获胜判定之前，与 Wathe 中时间耗尽优先于同一 tick 的 KILLERS 结果一致。
     */
    static Verdict verdict(boolean ownerActive, int soulCount, GameFunctions.WinStatus currentStatus,
                           int outsidersAlive) {
        if (!ownerActive) {
            return Verdict.END_AND_ABSTAIN;
        }
        if (soulCount == 0 || currentStatus == GameFunctions.WinStatus.TIME) {
            return Verdict.ABSTAIN;
        }
        return outsidersAlive != 0 ? Verdict.BLOCK : Verdict.WIN;
    }

    private static CheckWinCondition.WinResult checkWin(ServerWorld world, GameWorldComponent game,
                                                        GameFunctions.WinStatus currentStatus) {
        FerrymanMomentWorldComponent moment = FerrymanMomentWorldComponent.get(world);
        if (!moment.isActive()) {
            return null;
        }
        UUID ownerId = moment.owner();
        ServerPlayerEntity owner = ownerId == null ? null : world.getServer().getPlayerManager().getPlayer(ownerId);
        boolean ownerActive = owner != null && FerrymanParticipation.isActiveFerryman(owner);
        // Outsiders are counted only when the answer depends on them. / 只在结果取决于外部者时才计数。
        int outsiders = ownerActive && moment.soulCount() > 0 && currentStatus != GameFunctions.WinStatus.TIME
                ? FerrymanMomentRules.outsidersAlive(world, moment)
                : 0;
        return switch (verdict(ownerActive, moment.soulCount(), currentStatus, outsiders)) {
            case END_AND_ABSTAIN -> {
                // The Charon is gone: the Lost Souls die with him before normal resolution resumes.
                // 卡戎已不在：迷失的灵魂随他一同消亡，然后恢复常规结算。
                FerrymanMomentService.endWithSouls(world, moment);
                yield null;
            }
            case ABSTAIN -> null;
            case BLOCK -> CheckWinCondition.WinResult.block();
            case WIN -> CheckWinCondition.WinResult.neutralWin(owner, coWinners(world, moment));
        };
    }

    /**
     * The Charon never wins alone: every Lost Soul still standing is a co-winner, so the round resolves for the whole
     * moment instead of crediting only its owner (owner report: the souls were not winning with him).
     * 卡戎从不独自获胜：每一名仍存活的迷失灵魂都是并列胜利者，结算按整个时刻算，而不是只记在拥有者名下。
     */
    private static List<ServerPlayerEntity> coWinners(ServerWorld world, FerrymanMomentWorldComponent moment) {
        List<ServerPlayerEntity> coWinners = new ArrayList<>();
        for (UUID soulId : moment.souls()) {
            ServerPlayerEntity soul = world.getServer().getPlayerManager().getPlayer(soulId);
            if (FerrymanMomentRules.isSoulAlive(soul)) {
                coWinners.add(soul);
            }
        }
        return coWinners;
    }
}
