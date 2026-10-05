package dev.caecorthus.sparkwitch.mixin.seeker;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteStreaming;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.PlayerAssociatedNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Unlimited remote range, entity tracking (server, vanilla target, default remap): the owner always tracks the device
 * its live session shows, whatever the distance to the body, and every other entity is measured from that device, so
 * the owner sees what is around it. Both branches fall back to vanilla when {@link SeekerRemoteStreaming#focusOf}
 * returns null; the per-tick {@code updatePosition} re-evaluation in {@code SeekerRemoteStreaming} then restores
 * body-based tracking.
 * 无限遥控距离（实体追踪；服务端、原版目标、默认重映射）：拥有者始终追踪其存活会话正在显示的设备（无论与本体相距多远），
 * 其他实体则按与该设备的距离判断是否追踪，使拥有者能看到设备周围的事物。focusOf 返回 null 时两个分支都回退为原版；
 * SeekerRemoteStreaming 每刻调用 updatePosition 重新评估，从而恢复以本体为准的追踪。
 *
 * <p>Coexists with SparkStrength's drone tracker mixin (HEAD inject + {@code @ModifyExpressionValue} on the same
 * method): each HEAD inject cancels only for its own entity type and player, and the expression mixins chain.
 * NoellesRoles shadows the same fields for {@code sendToOtherNearbyPlayers} only. Default {@code require}.
 * 与 SparkStrength 无人机追踪 mixin（同一方法上的 HEAD 注入与 {@code @ModifyExpressionValue}）共存：各自的 HEAD 注入只对自己的
 * 实体类型与玩家取消，表达式 mixin 可链式叠加。NoellesRoles 也影射相同字段，但只用于 sendToOtherNearbyPlayers。使用默认 require。</p>
 */
@Mixin(targets = "net.minecraft.server.world.ServerChunkLoadingManager$EntityTracker")
public abstract class SeekerRemoteEntityTrackerMixin {
    @Shadow
    @Final
    Entity entity;

    @Shadow
    @Final
    EntityTrackerEntry entry;

    @Shadow
    @Final
    private Set<PlayerAssociatedNetworkHandler> listeners;

    @Inject(
            method = "updateTrackedStatus(Lnet/minecraft/server/network/ServerPlayerEntity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkwitch$alwaysTrackFocus(ServerPlayerEntity player, CallbackInfo ci) {
        if (!(entity instanceof SeekerDeviceEntity) || SeekerRemoteStreaming.focusOf(player) != entity) {
            return;
        }
        if (listeners.add(player.networkHandler)) {
            entry.startTracking(player);
        }
        ci.cancel();
    }

    @ModifyExpressionValue(
            method = "updateTrackedStatus(Lnet/minecraft/server/network/ServerPlayerEntity;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;getPos()Lnet/minecraft/util/math/Vec3d;")
    )
    private Vec3d sparkwitch$measureFromFocus(Vec3d original, @Local(argsOnly = true) ServerPlayerEntity player) {
        SeekerDeviceEntity focus = SeekerRemoteStreaming.focusOf(player);
        return focus == null ? original : focus.getPos();
    }
}
