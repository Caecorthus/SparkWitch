package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeTrainFallAttribution;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Covers authoritative direct network teleports and immediate victim disconnect cleanup.
 * 覆盖直接通过网络处理器完成的权威传送，并在受害者断线时立即清理。 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class JudgeNetworkTeleportRecoveryMixin {
    @Shadow public ServerPlayerEntity player;

    @Inject(method = "requestTeleport(DDDFFLjava/util/Set;)V", at = @At("TAIL"))
    private void sparkwitch$clearNetworkTeleport(CallbackInfo ci) {
        JudgeTrainFallAttribution.superseded(player);
    }

    @Inject(method = "onDisconnected", at = @At("HEAD"))
    private void sparkwitch$clearDisconnectedVictim(CallbackInfo ci) {
        JudgeTrainFallAttribution.superseded(player);
    }
}
