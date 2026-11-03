package dev.caecorthus.sparkwitch.mixin.stowaway;

import dev.caecorthus.sparkwitch.roles.neutral.stowaway.StowawayRules;
import dev.caecorthus.sparkwitch.roles.neutral.stowaway.StowawaySettlement;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.UUID;

/**
 * External seam (pinned Wathe {@code 1.5.6-spark-1.21.1}), owner rule 2026-11-02: a Stowaway still alive when the
 * round ends joins the winning side, and a dead one loses. Every writer of the round-end rows ends in {@code sync()}:
 * Wathe's two {@code setRoundEndData} overloads and SparkTraits' replacement of the team overload (it cancels Wathe's
 * body at HEAD and syncs its own rows). So the rows are completed at the HEAD of {@code sync()} on the server, after
 * whichever mod built them ({@link StowawaySettlement#completeRoundEndRows}); the Team Jiahao inject before the same
 * {@code sync()} has already run. Wathe's {@code didWin}, SparkTraits' replacement of it and
 * {@code GameRecordManager.endMatch} then read the same rows, so a Stowaway row decides {@code didWin} by its own
 * flag. Server only: the client component has no server and only reads the synced rows.
 * 外部接缝（固定 Wathe {@code 1.5.6-spark-1.21.1}），所有者规则 2026-11-02：回合结束时仍存活的偷渡客加入获胜一方，
 * 死亡的偷渡客判负。所有结算行写入方都以 {@code sync()} 结束：Wathe 的两个 {@code setRoundEndData} 重载，以及
 * SparkTraits 对阵营重载的替换（它在 HEAD 取消 Wathe 方法体并同步自己的行）。因此在服务端 {@code sync()} 的 HEAD、
 * 由任意模组生成行之后补全结算行（{@link StowawaySettlement#completeRoundEndRows}）；嘉豪阵营在同一 {@code sync()}
 * 之前的注入已先执行。Wathe 的 {@code didWin}、SparkTraits 对它的替换以及 {@code GameRecordManager.endMatch} 随后读取
 * 同一批行，因此偷渡客行的 {@code didWin} 由该行自身的标记决定。仅服务端：客户端组件没有服务器，只读取已同步的行。
 */
@Mixin(value = GameRoundEndComponent.class, remap = false, priority = 500)
public abstract class GameRoundEndComponentStowawayWinnerMixin {
    @Shadow
    @Final
    private List<GameRoundEndComponent.RoundEndData> players;

    @Shadow
    private GameFunctions.WinStatus winStatus;

    @Shadow
    @Final
    private MinecraftServer server;

    @Inject(method = "sync", at = @At("HEAD"), require = 1, allow = 1)
    private void sparkwitch$livingStowawaysJoinTheWin(CallbackInfo ci) {
        if (server != null) {
            StowawaySettlement.completeRoundEndRows(players, winStatus);
        }
    }

    /**
     * Priority 500 runs before SparkFactionAPI's and SparkTraits' HEADs on {@code didWin}; every other row falls
     * through to them unchanged.
     * 优先级 500 先于 SparkFactionAPI 与 SparkTraits 在 {@code didWin} 上的 HEAD；其他所有行原样交给它们。
     */
    @Inject(method = "didWin", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void sparkwitch$stowawayRowDecidesItsOwnWin(UUID uuid, CallbackInfoReturnable<Boolean> cir) {
        if (winStatus == GameFunctions.WinStatus.NONE) {
            return;
        }
        for (GameRoundEndComponent.RoundEndData row : players) {
            if (row.player().getId().equals(uuid)) {
                if (StowawayRules.ROLE_ID.equals(row.role())) {
                    cir.setReturnValue(row.isWinner());
                }
                return;
            }
        }
    }
}
