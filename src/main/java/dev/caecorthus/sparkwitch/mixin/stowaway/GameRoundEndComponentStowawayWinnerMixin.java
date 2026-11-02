package dev.caecorthus.sparkwitch.mixin.stowaway;

import dev.caecorthus.sparkwitch.roles.neutral.stowaway.StowawayRules;
import dev.caecorthus.sparkwitch.roles.neutral.stowaway.StowawaySettlement;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Round-end rule (owner, 2026-11-02): a Stowaway still alive when the round ends joins the winning team, while a dead
 * Stowaway loses. The row's {@code isWinner} flag is rewritten before the settlement sync, and {@code didWin} answers
 * true for a living Stowaway under a KILLERS/PASSENGERS/TIME result (Wathe otherwise decides that call from the role's
 * faction, where a neutral never wins). Explicit neutral wins and custom faction wins are handled by the collection
 * overload here and by {@code FactionWinnerCollectorStowawayWinnerMixin} respectively.
 * 回合结束规则（所有者 2026-11-02）：回合结束时仍活着的偷渡客加入获胜阵营，死亡的偷渡客判负。在结算同步之前改写该行的
 * {@code isWinner}，并在 KILLERS/PASSENGERS/TIME 结果下让 {@code didWin} 对存活的偷渡客返回 true（Wathe 原本按职业阵营
 * 判定，中立职业永远不会赢）。显式中立胜利由这里的集合重载处理，自定义阵营胜利由
 * {@code FactionWinnerCollectorStowawayWinnerMixin} 处理。
 */
@Mixin(value = GameRoundEndComponent.class, remap = false, priority = 500)
public abstract class GameRoundEndComponentStowawayWinnerMixin {
    @Shadow
    @Final
    private List<GameRoundEndComponent.RoundEndData> players;

    @Shadow
    private GameFunctions.WinStatus winStatus;

    @Shadow
    public abstract void sync();

    @Inject(
            method = "setRoundEndData(Lnet/minecraft/server/world/ServerWorld;Ldev/doctor4t/wathe/game/GameFunctions$WinStatus;)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/cca/GameRoundEndComponent;sync()V"),
            require = 1,
            allow = 1
    )
    private void sparkwitch$stowawayJoinsTeamWin(
            ServerWorld world,
            GameFunctions.WinStatus status,
            CallbackInfo ci
    ) {
        if (status == GameFunctions.WinStatus.KILLERS
                || status == GameFunctions.WinStatus.PASSENGERS
                || status == GameFunctions.WinStatus.TIME) {
            sparkwitch$markLivingStowaways(world);
        }
    }

    @Inject(
            method = "setRoundEndData(Lnet/minecraft/server/world/ServerWorld;Ljava/util/Collection;)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/cca/GameRoundEndComponent;sync()V"),
            require = 1,
            allow = 1
    )
    private void sparkwitch$stowawayJoinsExplicitWin(
            ServerWorld world,
            Collection<UUID> winners,
            CallbackInfo ci
    ) {
        if (!winners.isEmpty()) {
            sparkwitch$markLivingStowaways(world);
        }
    }

    /**
     * Safety net: rewrite again after the method's own sync and send a second sync. Some settlement writers sync before
     * the row list reaches its final shape; the RETURN pass guarantees the client receives the winner flag.
     * 兜底：方法自身的 sync 之后再改写一次并补发一次同步。部分结算写入方会在行列表定稿前先同步；RETURN 这一遍保证客户端
     * 收到赢家标记。
     */
    @Inject(
            method = "setRoundEndData(Lnet/minecraft/server/world/ServerWorld;Ldev/doctor4t/wathe/game/GameFunctions$WinStatus;)V",
            at = @At("RETURN"),
            require = 1,
            allow = 1
    )
    private void sparkwitch$stowawayJoinsTeamWinAfterReturn(
            ServerWorld world,
            GameFunctions.WinStatus status,
            CallbackInfo ci
    ) {
        if (status == GameFunctions.WinStatus.KILLERS
                || status == GameFunctions.WinStatus.PASSENGERS
                || status == GameFunctions.WinStatus.TIME) {
            sparkwitch$markLivingStowaways(world);
            sync();
        }
    }

    @Inject(
            method = "setRoundEndData(Lnet/minecraft/server/world/ServerWorld;Ljava/util/Collection;)V",
            at = @At("RETURN"),
            require = 1,
            allow = 1
    )
    private void sparkwitch$stowawayJoinsExplicitWinAfterReturn(
            ServerWorld world,
            Collection<UUID> winners,
            CallbackInfo ci
    ) {
        if (!winners.isEmpty()) {
            sparkwitch$markLivingStowaways(world);
            sync();
        }
    }

    @Inject(method = "didWin", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void sparkwitch$livingStowawayDidWin(UUID uuid, CallbackInfoReturnable<Boolean> cir) {
        if (winStatus != GameFunctions.WinStatus.KILLERS
                && winStatus != GameFunctions.WinStatus.PASSENGERS
                && winStatus != GameFunctions.WinStatus.TIME) {
            return;
        }
        for (GameRoundEndComponent.RoundEndData row : players) {
            if (row.player().getId().equals(uuid)
                    && StowawayRules.ROLE_ID.equals(row.role())
                    && row.endStatus() == GameRoundEndComponent.PlayerEndStatus.ALIVE) {
                cir.setReturnValue(true);
                return;
            }
        }
    }

    private void sparkwitch$markLivingStowaways(ServerWorld world) {
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        for (int index = 0; index < players.size(); index++) {
            GameRoundEndComponent.RoundEndData row = players.get(index);
            // Raw role from the game ledger: a display role (disguise) must not hide the Stowaway.
            // 从对局身份表读取原始职业：显示职业（伪装）不得掩盖偷渡客身份。
            if (StowawaySettlement.shouldBeWinner(
                    row.isWinner(), game.getRole(row.player().getId()), row.endStatus())) {
                players.set(index, new GameRoundEndComponent.RoundEndData(
                        row.player(), row.role(), row.endStatus(), true));
            }
        }
    }
}
