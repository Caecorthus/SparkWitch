package dev.caecorthus.sparkwitch.client.mixin.accountant;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.mixin.input.SelectedSlotFlushInvoker;
import dev.caecorthus.sparkwitch.net.AccountantWithdrawC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Left-click with the 存折 passbook in the main hand always means "take the coins out". It must be caught at
 * {@code doAttack}: in a round players are in adventure mode, so the vanilla attack-block path never runs and an air
 * swing sends nothing. Item-gated HEAD cancel (returns false: no swing, no attack), like the USEC and potion launcher
 * hooks; the server re-checks every gate, so this side only asks.
 * 主手持存折左键一律表示“取出金币”。只能在 {@code doAttack} 捕获：局内玩家处于冒险模式，原版攻击方块路径不会执行，空挥
 * 也不会发包。按物品门控的 HEAD 取消（返回 false：不挥手、不攻击），与 USEC、反坦克发射器的钩子相同；服务端会重新检查所有
 * 门槛，这一端只负责请求。
 */
@Mixin(MinecraftClient.class)
public abstract class AccountantAirSwingMixin {
    @Shadow
    @Nullable
    public ClientPlayerEntity player;

    @Shadow
    @Nullable
    public ClientPlayerInteractionManager interactionManager;

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$withdrawFromPassbook(CallbackInfoReturnable<Boolean> cir) {
        ClientPlayerEntity self = this.player;
        if (self == null || self.isSpectator()
                || !self.getMainHandStack().isOf(SparkWitchItems.accountantPassbook())) {
            return;
        }
        if (ClientPlayNetworking.canSend(AccountantWithdrawC2SPacket.ID)) {
            // The server reads its main-hand slot, but a hotbar key handled earlier in this tick changed only the
            // client's slot; flush it first, like the potion launcher and USEC fire (sends nothing when unchanged).
            // 服务端读取其主手栏位，但本刻早先处理的快捷栏按键只改变了客户端栏位；像炮筒与 USEC 开火一样先同步
            // （栏位未变时不发送任何内容）。
            if (this.interactionManager != null) {
                ((SelectedSlotFlushInvoker) this.interactionManager).sparkwitch$flushSelectedSlot();
            }
            ClientPlayNetworking.send(AccountantWithdrawC2SPacket.INSTANCE);
        }
        cir.setReturnValue(false);
    }
}
