package dev.caecorthus.sparkwitch.mixin.cat;

import dev.caecorthus.sparkwitch.roles.neutral.cat.SchrodingersCatService;
import net.minecraft.network.packet.c2s.play.SpectatorTeleportC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A cat in its spectre is a living spectator: vanilla would teleport it to any entity on a spectator-menu request (a
 * free peek, or a jump into another world). The request is dropped, injected right after
 * {@code NetworkThreadUtils.forceMainThread} like the FiendFeign guard, so it runs only on the server thread. Additive;
 * every other spectator is untouched.
 * 幽魂阶段的猫是活着的旁观者：原版会按旁观菜单请求把它传送到任意实体处（白看一眼，甚至跳到其他世界）。该请求被丢弃；与魔人
 * 假死守卫一样注入在 {@code NetworkThreadUtils.forceMainThread} 之后，因此只在服务端线程运行。叠加式；其他旁观者不受影响。
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerSchrodingersCatMixin {
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
    private void sparkwitch$blockCatSpectreSpectatorTeleport(SpectatorTeleportC2SPacket packet, CallbackInfo ci) {
        if (SchrodingersCatService.isSpectre(player)) {
            ci.cancel();
        }
    }
}
