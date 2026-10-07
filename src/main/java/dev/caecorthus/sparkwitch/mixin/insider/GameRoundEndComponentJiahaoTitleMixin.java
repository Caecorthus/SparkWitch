package dev.caecorthus.sparkwitch.mixin.insider;

import dev.caecorthus.sparkwitch.roles.neutral.insider.JiahaoTeamWinSeam;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.UUID;

/**
 * External seam (pinned Wathe {@code 1.5.6-spark-1.21.1}), owner decisions D2/D7, C14: Wathe's client titles a neutral
 * win with the first winning round-end row, and the explicit-winner overload builds rows in its role {@code HashMap}
 * order, not the primary winner's; a {@code WinResult} can only name online players. Just before that overload syncs,
 * a Team Jiahao win (a winning team row in a round with an Insider) marks every other team row as a winner, offline
 * {@code LEFT} / {@code LEFT_DEAD} rows included, and moves an Insider row first, so the end screen reads
 * "嘉豪阵营胜利！" whichever member's UUID hashes first. Wathe's {@code didWin} (and SparkTraits' replacement of it) and
 * {@code GameRecordManager.endMatch} read these rows, so they stay consistent. Every other win, and every round
 * without an Insider, keeps Wathe's rows. Additive {@code @Inject}; SparkFactionAPI's HEAD inject on the same overload
 * is unaffected. Server only.
 * 外部接缝（固定 Wathe {@code 1.5.6-spark-1.21.1}），所有者决定 D2/D7、C14：Wathe 客户端以第一条获胜结算行作为中立胜利
 * 标题，而显式胜者重载按职业 {@code HashMap} 顺序生成各行，与主胜者无关；{@code WinResult} 也只能列出在线玩家。在该重载
 * 同步之前，若为嘉豪阵营胜利（本局有内应且某条获胜行是阵营职业），则把其余所有阵营行（含离线的 {@code LEFT} /
 * {@code LEFT_DEAD} 行）标记为获胜，并把一条内应行移到最前，使结算界面不论哪个成员的 UUID 排在前面都显示“嘉豪阵营胜利！”。
 * Wathe 的 {@code didWin}（及 SparkTraits 对它的替换）与 {@code GameRecordManager.endMatch} 读取这些行，因此保持一致。
 * 其他胜利以及没有内应的回合保持 Wathe 原样。叠加式 {@code @Inject}；SparkFactionAPI 对同一重载的 HEAD 注入不受影响。
 * 仅服务端。
 */
@Mixin(value = GameRoundEndComponent.class, remap = false)
public abstract class GameRoundEndComponentJiahaoTitleMixin {
    @Inject(
            method = "setRoundEndData(Lnet/minecraft/server/world/ServerWorld;Ljava/util/Collection;)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/cca/GameRoundEndComponent;sync()V"),
            require = 1,
            allow = 1
    )
    private void sparkwitch$creditTeamJiahao(ServerWorld world, Collection<UUID> winnerUuids, CallbackInfo ci) {
        JiahaoTeamWinSeam.creditTeamJiahaoRows(world, ((GameRoundEndComponent) (Object) this).getPlayers());
    }
}
