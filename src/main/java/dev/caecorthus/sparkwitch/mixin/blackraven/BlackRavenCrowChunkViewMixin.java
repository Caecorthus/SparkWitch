package dev.caecorthus.sparkwitch.mixin.blackraven;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowEntity;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowStreaming;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Raven Form far view, chunk view (server, vanilla target, default remap), copied from
 * {@code SeekerRemoteChunkViewMixin}: while the owner flies a crow, its chunk view is centred on the crow instead of
 * the motionless body. {@code sendWatchPackets} runs every tick for watching players, so vanilla diffs the views,
 * streams the crow's chunks and unloads the rest; once {@link BlackRavenCrowStreaming#focusOf} returns null the body
 * centre comes back by itself. Only the client view moves: player chunk tickets stay on the body's section.
 * 化鸦远程视野（区块视野；服务端、原版目标、默认重映射），照搬搜寻者：主人操控乌鸦期间，其区块视野以乌鸦而非静止的本体为中心。
 * sendWatchPackets 每刻为观察区块的玩家执行，原版会比较新旧视野、推送乌鸦周围区块并卸载其余区块；focusOf 返回 null 后自动恢复为
 * 本体中心。只移动客户端视野：玩家区块票据仍位于本体所在区段。
 *
 * <p>Chains with the Seeker's and SparkStrength's drone mixins on the same call ({@code @ModifyExpressionValue}); each
 * returns the incoming value unless its own player has a live session. Default {@code require}.
 * 与搜寻者及 SparkStrength 无人机在同一调用上的 mixin 链式叠加；各自只在自己的玩家有有效会话时改写传入值。使用默认 require。</p>
 */
@Mixin(ServerChunkLoadingManager.class)
public abstract class BlackRavenCrowChunkViewMixin {
    @ModifyExpressionValue(
            method = "sendWatchPackets(Lnet/minecraft/server/network/ServerPlayerEntity;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;getChunkPos()Lnet/minecraft/util/math/ChunkPos;")
    )
    private ChunkPos sparkwitch$centreViewOnCrow(ChunkPos original,
                                                 @Local(argsOnly = true) ServerPlayerEntity player) {
        BlackRavenCrowEntity crow = BlackRavenCrowStreaming.focusOf(player);
        return crow == null ? original : crow.getChunkPos();
    }
}
