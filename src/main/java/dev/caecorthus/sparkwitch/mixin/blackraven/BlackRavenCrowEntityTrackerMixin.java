package dev.caecorthus.sparkwitch.mixin.blackraven;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowEntity;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowStreaming;
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
 * Raven Form far view, entity tracking (server, vanilla target, default remap), copied from
 * {@code SeekerRemoteEntityTrackerMixin}: the owner always tracks the crow it flies, whatever the distance to the
 * body, and every other entity is measured from the crow, so the owner sees what is around it. Both branches fall back
 * to vanilla when {@link BlackRavenCrowStreaming#focusOf} returns null; the per-tick {@code updatePosition} in
 * {@code BlackRavenCrowStreaming} then restores body-based tracking.
 * 化鸦远程视野（实体追踪；服务端、原版目标、默认重映射），照搬搜寻者：主人始终追踪自己操控的乌鸦（无论与本体相距多远），其他实体
 * 按与乌鸦的距离判断是否追踪，使主人能看到乌鸦周围的事物。focusOf 返回 null 时两个分支都回退为原版；BlackRavenCrowStreaming 每刻
 * 调用 updatePosition 恢复以本体为准的追踪。
 *
 * <p>Coexists with the Seeker's and SparkStrength's drone tracker mixins (HEAD inject + {@code @ModifyExpressionValue}
 * on the same method): each HEAD inject cancels only for its own entity type and player, and the expression mixins
 * chain. Default {@code require}.
 * 与搜寻者及 SparkStrength 无人机的追踪 mixin（同一方法上的 HEAD 注入与表达式修改）共存：各 HEAD 注入只对自己的实体类型与玩家取消，
 * 表达式 mixin 链式叠加。使用默认 require。</p>
 */
@Mixin(targets = "net.minecraft.server.world.ServerChunkLoadingManager$EntityTracker")
public abstract class BlackRavenCrowEntityTrackerMixin {
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
    private void sparkwitch$alwaysTrackOwnCrow(ServerPlayerEntity player, CallbackInfo ci) {
        if (!(entity instanceof BlackRavenCrowEntity) || BlackRavenCrowStreaming.focusOf(player) != entity) {
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
    private Vec3d sparkwitch$measureFromCrow(Vec3d original, @Local(argsOnly = true) ServerPlayerEntity player) {
        BlackRavenCrowEntity crow = BlackRavenCrowStreaming.focusOf(player);
        return crow == null ? original : crow.getPos();
    }
}
