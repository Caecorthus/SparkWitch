package dev.caecorthus.sparkwitch.mixin.stowaway;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.neutral.stowaway.StowawayWinScope;
import dev.doctor4t.wathe.api.event.CheckWinCondition;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.game.gamemode.MurderGameMode;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Wraps Wathe's single {@code CheckWinCondition} dispatch so every listener runs inside
 * {@link StowawayWinScope}: while a win is being determined a Stowaway answers false to
 * {@code GameFunctions.isPlayerPlayingAndAlive}, exactly as if it had died. The base Wathe killer/passenger checks
 * before this dispatch are untouched (a neutral Stowaway is already skipped by {@code isInnocent}), and the actual
 * death state stays intact for the round-end card. Server only.
 * 包装 Wathe 唯一的 {@code CheckWinCondition} 分发，使所有监听器都在 {@link StowawayWinScope} 内运行：判定胜负时
 * 偷渡客对 {@code GameFunctions.isPlayerPlayingAndAlive} 返回 false，等同于已经死亡。分发之前的 Wathe 原生杀手/乘客
 * 判断保持不变（中立偷渡客本就被 {@code isInnocent} 跳过），真实死亡状态也保持不变，供结算卡片使用。仅服务端。
 */
@Mixin(value = MurderGameMode.class, remap = false)
public abstract class MurderGameModeStowawayWinScopeMixin {
    @WrapOperation(
            method = "tickServerGameLoop",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/api/event/CheckWinCondition;checkWin(Lnet/minecraft/server/world/ServerWorld;Ldev/doctor4t/wathe/cca/GameWorldComponent;Ldev/doctor4t/wathe/game/GameFunctions$WinStatus;)Ldev/doctor4t/wathe/api/event/CheckWinCondition$WinResult;"
            ),
            require = 1,
            allow = 1
    )
    private static CheckWinCondition.WinResult sparkwitch$stowawayCountsAsDeadForWin(
            CheckWinCondition event,
            ServerWorld world,
            GameWorldComponent game,
            GameFunctions.WinStatus status,
            Operation<CheckWinCondition.WinResult> original
    ) {
        return StowawayWinScope.withinWinCheck(() -> original.call(event, world, game, status));
    }
}
