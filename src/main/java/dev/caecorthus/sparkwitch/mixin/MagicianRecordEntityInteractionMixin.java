package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianServerHooks;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 补录对实体右键；部分扩展物品不会经过普通 interactItem 链。 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class MagicianRecordEntityInteractionMixin {
    @Shadow @Final public ServerPlayerEntity player;

    @Inject(method = "onPlayerInteractEntity", at = @At("RETURN"))
    private void sparkwitch$recordEntityUse(PlayerInteractEntityC2SPacket packet, CallbackInfo ci) {
        packet.handle(new PlayerInteractEntityC2SPacket.Handler() {
            @Override public void interact(Hand hand) { MagicianServerHooks.recordUse(player, hand); }
            @Override public void interactAt(Hand hand, net.minecraft.util.math.Vec3d pos) { MagicianServerHooks.recordUse(player, hand); }
            @Override public void attack() { }
        });
    }
}
