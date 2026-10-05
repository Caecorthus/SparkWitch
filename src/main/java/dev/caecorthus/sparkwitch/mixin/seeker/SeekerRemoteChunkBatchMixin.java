package dev.caecorthus.sparkwitch.mixin.seeker;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteStreaming;
import net.minecraft.server.network.ChunkDataSender;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Unlimited remote range, chunk batches (server, vanilla target, default remap): queued chunks are sent nearest-first
 * around the session's focus device (matching {@link SeekerRemoteChunkViewMixin}), so the area the owner looks at
 * arrives first. Falls back to the body when {@link SeekerRemoteStreaming#focusOf} returns null.
 * 无限遥控距离（区块批次；服务端、原版目标、默认重映射）：排队的区块按与会话焦点设备的距离由近到远发送（与
 * SeekerRemoteChunkViewMixin 一致），使拥有者正在看的区域最先到达。focusOf 返回 null 时回退为本体位置。
 *
 * <p>Coexists with SparkStrength's drone batch mixin through {@code @ModifyExpressionValue} chaining; default
 * {@code require} (Seeker mixin rule).
 * 通过 {@code @ModifyExpressionValue} 链式叠加与 SparkStrength 的无人机批次 mixin 共存；使用默认 {@code require}（搜寻者 mixin 规则）。</p>
 */
@Mixin(ChunkDataSender.class)
public abstract class SeekerRemoteChunkBatchMixin {
    @ModifyExpressionValue(
            method = "sendChunkBatches(Lnet/minecraft/server/network/ServerPlayerEntity;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;getChunkPos()Lnet/minecraft/util/math/ChunkPos;")
    )
    private ChunkPos sparkwitch$batchAroundFocus(ChunkPos original,
                                                 @Local(argsOnly = true) ServerPlayerEntity player) {
        SeekerDeviceEntity focus = SeekerRemoteStreaming.focusOf(player);
        return focus == null ? original : focus.getChunkPos();
    }
}
