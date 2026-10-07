package dev.caecorthus.sparkwitch.mixin.seeker;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteStreaming;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Unlimited remote range, chunk view (server, vanilla target, default remap): while the owner's live session shows a
 * car or camera, the owner's chunk view is centred on that device instead of the motionless body.
 * {@code tickEntityMovement} calls {@code sendWatchPackets} every tick for watching players, so vanilla diffs the old
 * and new view, sends the render-distance centre, streams the device's chunks and unloads the rest; once
 * {@link SeekerRemoteStreaming#focusOf} returns null the body centre comes back on the next tick by itself. Only the
 * client view moves: player chunk tickets stay on the body's real section.
 * 无限遥控距离（区块视野；服务端、原版目标、默认重映射）：拥有者的存活会话显示小车或摄像头期间，其区块视野以该设备而非静止的本体为中心。
 * tickEntityMovement 每刻都会为观察区块的玩家调用 sendWatchPackets，因此原版会比较新旧视野、发送渲染中心、推送设备周围区块并卸载其余区块；
 * focusOf 返回 null 后，下一刻自动恢复为本体中心。只移动客户端视野：玩家区块票据仍位于本体真实所在区段。
 *
 * <p>Coexists with SparkStrength's drone mixin on the same call: {@code @ModifyExpressionValue} chains, and each
 * returns the incoming value unless its own player has a live session. Default {@code require} (Seeker mixin rule).
 * 与 SparkStrength 无人机 mixin 在同一调用上共存：{@code @ModifyExpressionValue} 可链式叠加，各自只在自己的玩家有存活会话时
 * 改写传入值。使用默认 {@code require}（搜寻者 mixin 规则）。</p>
 */
@Mixin(ServerChunkLoadingManager.class)
public abstract class SeekerRemoteChunkViewMixin {
    @ModifyExpressionValue(
            method = "sendWatchPackets(Lnet/minecraft/server/network/ServerPlayerEntity;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;getChunkPos()Lnet/minecraft/util/math/ChunkPos;")
    )
    private ChunkPos sparkwitch$centreViewOnFocus(ChunkPos original,
                                                  @Local(argsOnly = true) ServerPlayerEntity player) {
        SeekerDeviceEntity focus = SeekerRemoteStreaming.focusOf(player);
        return focus == null ? original : focus.getChunkPos();
    }
}
