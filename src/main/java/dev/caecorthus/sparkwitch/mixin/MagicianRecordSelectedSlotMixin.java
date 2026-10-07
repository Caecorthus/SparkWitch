package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianServerHooks;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在服务端接受快捷栏切换后记录槽位，确保播放时真正拿到录制中的物品。 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class MagicianRecordSelectedSlotMixin {
    @Shadow @Final public ServerPlayerEntity player;

    @Inject(method = "onUpdateSelectedSlot", at = @At("RETURN"))
    private void sparkwitch$recordSelectedSlot(UpdateSelectedSlotC2SPacket packet, CallbackInfo ci) {
        MagicianServerHooks.recordSlot(player, packet.getSelectedSlot());
    }
}
