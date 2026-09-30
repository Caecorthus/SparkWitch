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
 * External seam (pinned Wathe {@code 1.5.6-spark-1.21.1}), owner decision D7: Wathe's client titles a neutral win with
 * the first winning round-end row, and the explicit-winner overload builds rows in its role {@code HashMap} order, not
 * the primary winner's. Just before that overload syncs, a winning Insider row moves to the front so a Team Jiahao win
 * reads "嘉豪阵营胜利！" whichever member's UUID hashes first. Rows without a winning Insider keep Wathe's order. Additive
 * {@code @Inject}; SparkFactionAPI's HEAD inject on the same overload is unaffected. Server only.
 * 外部接缝（固定 Wathe {@code 1.5.6-spark-1.21.1}），所有者决定 D7：Wathe 客户端以第一条获胜结算行作为中立胜利标题，
 * 而显式胜者重载按职业 {@code HashMap} 顺序生成各行，与主胜者无关。在该重载同步之前，把获胜的内应行移到最前，
 * 使嘉豪阵营胜利不论哪个成员的 UUID 排在前面都显示“嘉豪阵营胜利！”。没有获胜内应时保持 Wathe 原顺序。叠加式
 * {@code @Inject}；SparkFactionAPI 对同一重载的 HEAD 注入不受影响。仅服务端。
 */
@Mixin(value = GameRoundEndComponent.class, remap = false)
public abstract class GameRoundEndComponentJiahaoTitleMixin {
    @Inject(
            method = "setRoundEndData(Lnet/minecraft/server/world/ServerWorld;Ljava/util/Collection;)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/cca/GameRoundEndComponent;sync()V"),
            require = 1,
            allow = 1
    )
    private void sparkwitch$leadWithTeamJiahaoTitle(ServerWorld world, Collection<UUID> winnerUuids, CallbackInfo ci) {
        JiahaoTeamWinSeam.leadWithTeamJiahaoTitle(((GameRoundEndComponent) (Object) this).getPlayers());
    }
}
