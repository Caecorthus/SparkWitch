package dev.caecorthus.sparkwitch.mixin.blackraven;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowEntity;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowStreaming;
import net.minecraft.server.network.ChunkDataSender;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Raven Form far view, chunk batches (server, vanilla target, default remap), copied from
 * {@code SeekerRemoteChunkBatchMixin}: queued chunks are sent nearest-first around the flown crow (matching
 * {@link BlackRavenCrowChunkViewMixin}), so the area the owner looks at arrives first. Falls back to the body when
 * {@link BlackRavenCrowStreaming#focusOf} returns null. Chains with the Seeker's and the drone's batch mixins.
 * 化鸦远程视野（区块批次；服务端、原版目标、默认重映射），照搬搜寻者：排队区块按与被操控乌鸦的距离由近到远发送（与区块视野 mixin
 * 一致），使主人正在看的区域最先到达。focusOf 返回 null 时回退为本体位置。与搜寻者及无人机的批次 mixin 链式叠加。
 */
@Mixin(ChunkDataSender.class)
public abstract class BlackRavenCrowChunkBatchMixin {
    @ModifyExpressionValue(
            method = "sendChunkBatches(Lnet/minecraft/server/network/ServerPlayerEntity;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;getChunkPos()Lnet/minecraft/util/math/ChunkPos;")
    )
    private ChunkPos sparkwitch$batchAroundCrow(ChunkPos original,
                                                @Local(argsOnly = true) ServerPlayerEntity player) {
        BlackRavenCrowEntity crow = BlackRavenCrowStreaming.focusOf(player);
        return crow == null ? original : crow.getChunkPos();
    }
}
