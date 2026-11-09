package dev.caecorthus.sparkwitch.mixin.bear;

import dev.caecorthus.sparkwitch.headset.HeadsetSlots;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server seam for the creative inventory tab: vanilla's {@code onCreativeInventoryAction} only writes handler slots
 * 1..45, so a creative edit of the headset slot (handler 46) would be dropped. At TAIL (reached only on the server
 * thread, after vanilla's own handling, which ignores slot 46) a creative player's packet for slot 46 is applied when it
 * clears the slot or holds one item the slot accepts; anything else is ignored, so even a creative client cannot put a
 * foreign item there. Vanilla's other slots and the drop branch are untouched.
 * 创造模式物品栏页的服务端接缝：原版 {@code onCreativeInventoryAction} 只写入界面栏位 1..45，创造模式对耳机栏位（界面 46）的
 * 修改会被丢弃。在 TAIL（只会在服务端线程、原版处理之后到达；原版忽略 46 号栏位）对创造模式玩家发往 46 号栏位的数据包，若是清空
 * 该栏位或放入一件该栏位接受的物品则予以应用；其他一律忽略，因此即使创造模式客户端也无法放入其他物品。原版其他栏位与丢弃分支不变。
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class HeadsetCreativeActionMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onCreativeInventoryAction(Lnet/minecraft/network/packet/c2s/play/CreativeInventoryActionC2SPacket;)V",
            at = @At("TAIL"))
    private void sparkwitch$creativeHeadsetSlot(CreativeInventoryActionC2SPacket packet, CallbackInfo ci) {
        if (packet.slot() != HeadsetSlots.HANDLER_SLOT_ID || !player.interactionManager.isCreative()
                || player.playerScreenHandler.slots.size() <= HeadsetSlots.HANDLER_SLOT_ID) {
            return;
        }
        if (HeadsetSlots.acceptsCreativeWrite(packet.stack())) {
            player.playerScreenHandler.getSlot(HeadsetSlots.HANDLER_SLOT_ID).setStack(packet.stack());
            player.playerScreenHandler.sendContentUpdates();
        }
    }
}
