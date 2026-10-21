package dev.caecorthus.sparkwitch.mixin.blackraven;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowForm;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server inventory lock of the frozen Raven Form body (vanilla target, default remap): while the player is in crow
 * form, every slot click (moves, throws, swaps) and the drop, drop-stack and hand-swap actions are ignored and the
 * handler is resynced, because the client may already have predicted the change. Both injections sit after
 * {@code NetworkThreadUtils.forceMainThread}, so they only run on the server thread. Other players are untouched.
 * 化鸦期间冻结本体的服务端物品栏锁（原版目标，默认重映射）：玩家处于化鸦时，忽略一切槽位点击（移动、丢出、交换）以及丢弃、丢弃整组与换手动作，
 * 并重新同步界面，因为客户端可能已预测了变化。两个注入点都位于 {@code NetworkThreadUtils.forceMainThread} 之后，因此只在服务端线程运行。
 * 其他玩家不受影响。
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class BlackRavenCrowBodyInventoryGuardMixin {
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
    private void sparkwitch$ignoreCrowBodyClicks(ClickSlotC2SPacket packet, CallbackInfo ci) {
        if (BlackRavenCrowForm.isInCrowForm(player)) {
            player.currentScreenHandler.syncState();
            ci.cancel();
        }
    }

    @Inject(
            method = "onPlayerAction(Lnet/minecraft/network/packet/c2s/play/PlayerActionC2SPacket;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void sparkwitch$ignoreCrowBodyDropsAndSwaps(PlayerActionC2SPacket packet, CallbackInfo ci) {
        PlayerActionC2SPacket.Action action = packet.getAction();
        if ((action == PlayerActionC2SPacket.Action.DROP_ITEM
                || action == PlayerActionC2SPacket.Action.DROP_ALL_ITEMS
                || action == PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND)
                && BlackRavenCrowForm.isInCrowForm(player)) {
            player.currentScreenHandler.syncState();
            ci.cancel();
        }
    }
}
