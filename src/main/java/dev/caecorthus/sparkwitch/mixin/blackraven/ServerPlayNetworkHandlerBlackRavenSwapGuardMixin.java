package dev.caecorthus.sparkwitch.mixin.blackraven;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseService;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server swap guard: ignores ClickSlotC2SPacket from a Raven for 20 ticks after an identity change and resyncs
 * the open handler, so a click predicted against the old identity's slots never moves or drops the new set.
 * Injected after {@code NetworkThreadUtils.forceMainThread}, so it only runs on the server thread.
 * 服务端交换保护：身份变化后 20 刻内忽略黑羽鸦的 ClickSlotC2SPacket 并重新同步当前界面，
 * 避免按旧身份槽位预测的点击移动或丢弃新物品。注入点位于 forceMainThread 之后，因此只在服务端线程运行。
 */
@Mixin(value = ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerBlackRavenSwapGuardMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(
            method = "onClickSlot(Lnet/minecraft/network/packet/c2s/play/ClickSlotC2SPacket;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void sparkwitch$ignoreClicksAfterDisguiseSwap(ClickSlotC2SPacket packet, CallbackInfo ci) {
        if (BlackRavenDisguiseService.isWithinPostSwitchGuard(player)) {
            player.currentScreenHandler.syncState();
            ci.cancel();
        }
    }
}
