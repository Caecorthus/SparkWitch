package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.event.CheckWinCondition;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable contract: the Fiend Moment win listener runs in {@link #PHASE}, ordered before {@link Event#DEFAULT_PHASE}
 * on Wathe's first-non-null {@code CheckWinCondition}, so it decides before NoellesRoles, SparkTraits, the Murderous
 * Witch and SparkFactionAPI's faction wins (all default phase). No moment: {@code null}. Invalid moment (Fiend gone,
 * dead, swallowed by a Taotie, no longer the Fiend, or another match): end it, then {@code null} so normal resolution
 * resumes this tick. The swallow is polled here every tick from NoellesRoles' synced
 * {@code SwallowedPlayerComponent}; a swallowed Fiend never wins and is marked spent.
 * Completed with the Fiend alive: the Fiend wins alone. Otherwise every other win, TIME included, is suspended (D2).
 * 稳定契约：魔人时刻胜负监听器运行于 {@link #PHASE}，在 Wathe「首个非 null 生效」的 {@code CheckWinCondition} 上排在
 * {@link Event#DEFAULT_PHASE} 之前，因此先于 NoellesRoles、SparkTraits、杀意魔女与 SparkFactionAPI 阵营胜利（均为默认阶段）
 * 作出决定。无时刻：{@code null}。时刻失效（魔人离开、死亡、被饕餮吞噬、不再是魔人或对局已变）：结束时刻后返回
 * {@code null}，本 tick 即恢复常规结算。吞噬状态每 tick 从 NoellesRoles 已同步的 {@code SwallowedPlayerComponent} 轮询；
 * 被吞噬的魔人绝不获胜，并被登记为已耗尽。魔人存活且时刻完成：魔人独自获胜。其余情况暂停所有其他胜利，包括 TIME（D2）。
 */
public final class FiendWinService {
    public static final Identifier PHASE = SparkWitch.id("fiend_moment_win");
    private static boolean registered;

    private FiendWinService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        CheckWinCondition.EVENT.addPhaseOrdering(PHASE, Event.DEFAULT_PHASE);
        CheckWinCondition.EVENT.register(PHASE, FiendWinService::checkWin);
    }

    private static CheckWinCondition.WinResult checkWin(ServerWorld world, GameWorldComponent game,
                                                        GameFunctions.WinStatus currentStatus) {
        FiendMomentWorldComponent moment = FiendMomentWorldComponent.get(world);
        UUID fiendId = moment.fiend();
        if (fiendId == null) {
            return null;
        }
        PlayerEntity entity = world.getPlayerByUuid(fiendId);
        ServerPlayerEntity fiend = entity instanceof ServerPlayerEntity serverPlayer ? serverPlayer : null;
        FiendMomentRules.EndReason invalidation = FiendMomentRules.invalidation(
                Objects.equals(moment.matchId(), FiendMomentService.currentMatchId()),
                fiend != null,
                fiend != null && GameFunctions.isPlayerPlayingAndAlive(fiend),
                fiend != null && SwallowedPlayerComponent.isPlayerSwallowed(fiend),
                FiendParticipation.isFiendRole(game.getRole(fiendId)));
        return switch (FiendMomentRules.decide(moment.isActive(), invalidation, moment.isComplete())) {
            case ABSTAIN -> null;
            case END -> {
                FiendMomentService.end(world, invalidation);
                yield null;
            }
            case BLOCK -> CheckWinCondition.WinResult.block();
            case WIN -> CheckWinCondition.WinResult.neutralWin(fiend);
        };
    }
}
