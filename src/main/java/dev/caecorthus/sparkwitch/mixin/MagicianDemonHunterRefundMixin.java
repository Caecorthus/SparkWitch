package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPuppetHits;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.agmas.noellesroles.demonhunter.DemonHunterShootC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server Demon Hunter pistol receiver TAIL (NoellesRoles): a shot that ended a Jester puppet
 * ({@code MagicianPuppetHits.onDemonHunterPayload}, at the receiver's target lookup) earns NoellesRoles' own Jester
 * bullet refund. NoellesRoles spends the bullet from a count it read before that lookup, so the refund can only be paid
 * here, after its spend and possible removal of the emptied pistol: its only return past the lookup is the final one,
 * which {@code TAIL} targets. Never cancels. Class-level {@code remap = false}: NoellesRoles/Fabric member.
 * 服务端猎魔枪接收器 TAIL（NoellesRoles）：结束了小丑皮套的一枪（在接收器目标查找处由
 * {@code MagicianPuppetHits.onDemonHunterPayload} 判定）获得 NoellesRoles 自身的小丑返还子弹。NoellesRoles 按查找之前读取的
 * 数量扣除子弹，因此只能在其扣除（及可能移除打空的手枪）之后于此处支付：查找之后唯一的返回就是最后一个返回，即 {@code TAIL}
 * 所指。从不取消。类级 {@code remap = false}：NoellesRoles/Fabric 成员。
 */
@Mixin(value = DemonHunterShootC2SPacket.Receiver.class, remap = false)
public abstract class MagicianDemonHunterRefundMixin {
    @Inject(method = "receive(Lorg/agmas/noellesroles/demonhunter/DemonHunterShootC2SPacket;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At("TAIL"))
    private void sparkwitch$refundJesterPuppetShot(DemonHunterShootC2SPacket payload,
                                                  ServerPlayNetworking.Context context, CallbackInfo ci) {
        MagicianPuppetHits.afterDemonHunterShot(context.player());
    }
}
