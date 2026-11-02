package dev.caecorthus.sparkwitch.client.mixin.stowaway;

import dev.caecorthus.sparkwitch.roles.neutral.stowaway.StowawayRules;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side settlement fallback: even if a synced row missed the server rewrite, the renderer still groups a living
 * Stowaway with the winners for KILLERS/PASSENGERS/TIME. This is the flag {@code RoundTextRenderer} reads when it splits
 * entries into the winner and loser sections.
 * 客户端结算兜底：即使同步下来的行漏掉了服务端改写，对于 KILLERS/PASSENGERS/TIME，渲染器仍会把活着的偷渡客归入胜者组。
 * 这正是 {@code RoundTextRenderer} 划分胜者/失败者区块时读取的标记。
 */
@Mixin(targets = "dev.doctor4t.wathe.cca.GameRoundEndComponent$RoundEndData", remap = false)
public abstract class StowawayRoundEndRowMixin {
    @Shadow
    public abstract Identifier role();

    @Shadow
    public abstract GameRoundEndComponent.PlayerEndStatus endStatus();

    @Inject(method = "isWinner", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$livingStowawayShowsAsWinner(CallbackInfoReturnable<Boolean> cir) {
        if (!StowawayRules.ROLE_ID.equals(role())
                || endStatus() != GameRoundEndComponent.PlayerEndStatus.ALIVE) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        // Only the render thread may use this fallback; the integrated server shares the class but must see the real
        // synced field when it builds settlement rows.
        // 只有渲染线程可以使用这个兜底；集成服务端共享同一个类，但构建结算行时必须看到真实同步字段。
        if (!client.isOnThread() || client.player == null) {
            return;
        }
        GameFunctions.WinStatus status = GameRoundEndComponent.KEY.get(client.player.getScoreboard()).getWinStatus();
        if (status == GameFunctions.WinStatus.KILLERS
                || status == GameFunctions.WinStatus.PASSENGERS
                || status == GameFunctions.WinStatus.TIME) {
            cir.setReturnValue(true);
        }
    }
}
