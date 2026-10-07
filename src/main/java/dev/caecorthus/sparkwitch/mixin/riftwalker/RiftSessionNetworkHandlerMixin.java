package dev.caecorthus.sparkwitch.mixin.riftwalker;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.SpectatorTeleportC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Server mixin for the Rift Gate alive-spectator body (research 03 §3.3, vectors 2 and 4).
 * <ul>
 *   <li>{@code onSpectatorTeleport}: vanilla teleports any SPECTATOR to any entity UUID in any world with no range
 *   check; an occupant's request is dropped. Injected right after {@code NetworkThreadUtils.forceMainThread}, so it runs
 *   only on the server thread (NR's Taotie guard uses HEAD for swallowed players only).</li>
 *   <li>{@code requestTeleport(DDDFFSet)}: the funnel of every server-side player teleport. Any that is not the
 *   session's own anchor move is reported to {@link RiftSessionService#onTeleportRequested}; the next tick ends the
 *   session as BODY_MOVED only for such a foreign move beyond the tolerance.</li>
 *   <li>{@code onPlayerMove} / {@code onVehicleMove} (B-1): wrapped so a teleport vanilla issues while it handles this
 *   player's OWN move packet (the "moved too quickly" / "moved wrongly" resets and the pending-teleport resend, all at
 *   a client-chosen position) is reported as such and never counts as a foreign move: the occupant is snapped back to
 *   the anchor instead of leaving where a modified client put it. The depth is per handler and counted only on the
 *   server thread (the first, off-thread call throws inside {@code forceMainThread} and is rescheduled).</li>
 * </ul>
 * 裂隙门「活着的旁观者」本体的服务端 mixin（调研 03 §3.3，第 2 与第 4 条）。
 * {@code onSpectatorTeleport}：原版会把任何旁观者无距离限制地传送到任意世界的任意实体 UUID 处；门内玩家的请求被丢弃。注入点在
 * {@code NetworkThreadUtils.forceMainThread} 之后，因此只在服务端线程运行（NR 的饕餮防护只对被吞者在 HEAD 拦截）。
 * {@code requestTeleport(DDDFFSet)}：所有服务端玩家传送的汇聚点。凡不是会话自身锚点移动的传送都报告给
 * {@link RiftSessionService#onTeleportRequested}；只有超出容差的此类外部移动才会让下一刻以 BODY_MOVED 结束会话。
 * {@code onPlayerMove} / {@code onVehicleMove}（B-1）：被包装，使原版在处理该玩家自己的移动包时发出的传送（「移动过快」/
 * 「移动异常」重置与待确认传送重发，位置都由客户端决定）按此上报、绝不算作外部移动：门内玩家会被拉回锚点，而不是停在修改过的
 * 客户端指定的位置。深度按处理器计数且只在服务端线程累加（首次非主线程调用会在 {@code forceMainThread} 中抛出并被重新调度）。
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class RiftSessionNetworkHandlerMixin {
    @Shadow
    public ServerPlayerEntity player;

    /** Server-thread depth of this player's own move-packet handling. / 本玩家移动包处理的服务端线程嵌套深度。 */
    @Unique
    private int sparkwitch$ownMovePacketDepth;

    @Inject(
            method = "onSpectatorTeleport",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;"
                            + "Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V",
                    shift = At.Shift.AFTER),
            cancellable = true
    )
    private void sparkwitch$blockRiftSpectatorTeleport(SpectatorTeleportC2SPacket packet, CallbackInfo ci) {
        if (RiftSessionService.isInside(player)) {
            ci.cancel();
        }
    }

    @WrapMethod(method = "onPlayerMove")
    private void sparkwitch$markOwnPlayerMove(PlayerMoveC2SPacket packet, Operation<Void> original) {
        boolean counted = sparkwitch$enterOwnMovePacket();
        try {
            original.call(packet);
        } finally {
            sparkwitch$leaveOwnMovePacket(counted);
        }
    }

    @WrapMethod(method = "onVehicleMove")
    private void sparkwitch$markOwnVehicleMove(VehicleMoveC2SPacket packet, Operation<Void> original) {
        boolean counted = sparkwitch$enterOwnMovePacket();
        try {
            original.call(packet);
        } finally {
            sparkwitch$leaveOwnMovePacket(counted);
        }
    }

    @Inject(method = "requestTeleport(DDDFFLjava/util/Set;)V", at = @At("HEAD"))
    private void sparkwitch$noteForeignRiftTeleport(double x, double y, double z, float yaw, float pitch,
                                                     Set<PositionFlag> flags, CallbackInfo ci) {
        RiftSessionService.onTeleportRequested(player, sparkwitch$ownMovePacketDepth > 0);
    }

    @Unique
    private boolean sparkwitch$enterOwnMovePacket() {
        ServerPlayerEntity current = player;
        if (current == null || current.server == null || !current.server.isOnThread()) {
            return false;
        }
        sparkwitch$ownMovePacketDepth++;
        return true;
    }

    @Unique
    private void sparkwitch$leaveOwnMovePacket(boolean counted) {
        if (counted && sparkwitch$ownMovePacketDepth > 0) {
            sparkwitch$ownMovePacketDepth--;
        }
    }
}
