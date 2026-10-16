package dev.caecorthus.sparkwitch.mixin.fiend;

import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendFeignService;
import net.minecraft.network.packet.c2s.play.SpectatorTeleportC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A feigning Fiend is a living spectator: vanilla would teleport it to any entity on a spectator-menu request, a free
 * peek elsewhere before the feign hold pulls it back. The request is dropped, injected right after
 * {@code NetworkThreadUtils.forceMainThread} like the Rift Gate guard, so it runs only on the server thread. Additive;
 * every other spectator is untouched.
 * 假死中的魔人是活着的旁观者：原版会按旁观菜单请求把它传送到任意实体处，在假死固定把它拉回之前白看一眼别处。该请求被丢弃；
 * 与裂隙门守卫一样注入在 {@code NetworkThreadUtils.forceMainThread} 之后，因此只在服务端线程运行。叠加式；其他旁观者不受影响。
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerFiendFeignMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(
            method = "onSpectatorTeleport",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;"
                            + "Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V",
                    shift = At.Shift.AFTER),
            cancellable = true
    )
    private void sparkwitch$blockFeigningFiendSpectatorTeleport(SpectatorTeleportC2SPacket packet, CallbackInfo ci) {
        if (FiendFeignService.isFeigning(player)) {
            ci.cancel();
        }
    }
}
