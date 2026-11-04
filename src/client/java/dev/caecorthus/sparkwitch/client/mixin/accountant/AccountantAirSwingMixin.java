package dev.caecorthus.sparkwitch.client.mixin.accountant;

import dev.caecorthus.sparkwitch.net.AccountantWithdrawC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Left-click with the passbook in hand always means "take the coins back".
 * <p>It has to be caught here rather than in an attack callback: inside a round the player is in adventure mode, where
 * the client refuses to run the block/entity attack paths at all, so those callbacks never fire. The swing is then
 * cancelled so the accountant can never hurt anyone with a passbook.
 * 手持存折左键一律表示「取回金币」。
 * <p>只能在这里捕获，不能靠攻击回调：局内玩家处于冒险模式，客户端根本不会走攻击方块/实体的路径，
 * 那两个回调永远不会触发。捕获后取消这次挥击，会计拿着存折伤不到任何人。
 */
@Mixin(MinecraftClient.class)
public abstract class AccountantAirSwingMixin {

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$withdrawOnLeftClick(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }
        if (!player.getMainHandStack().isOf(
                dev.caecorthus.sparkwitch.SparkWitchItems.accountantPassbook())) {
            return;
        }
        if (ClientPlayNetworking.canSend(AccountantWithdrawC2SPacket.ID)) {
            ClientPlayNetworking.send(new AccountantWithdrawC2SPacket());
        }
        // Swallow the swing: no attack, no block breaking. 吞掉这次挥击：不攻击、不破坏方块。
        cir.setReturnValue(false);
        cir.cancel();
    }
}
