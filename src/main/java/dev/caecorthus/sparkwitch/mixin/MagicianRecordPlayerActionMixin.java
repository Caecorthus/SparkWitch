package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianServerHooks;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 补录蓄力物品松手和纯视觉挥手，保证手雷/投掷物/空挥能按原轨迹播放。 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class MagicianRecordPlayerActionMixin {
    @Shadow @Final public ServerPlayerEntity player;

    @Inject(method = "onPlayerAction", at = @At("RETURN"))
    private void sparkwitch$recordRelease(PlayerActionC2SPacket packet, CallbackInfo ci) {
        if (packet.getAction() == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM) {
            MagicianServerHooks.recordRelease(player);
        }
    }

    @Inject(method = "onHandSwing", at = @At("RETURN"))
    private void sparkwitch$recordSwing(HandSwingC2SPacket packet, CallbackInfo ci) {
        MagicianServerHooks.recordSwing(player, packet.getHand());
    }
}
