package dev.caecorthus.sparkwitch.mixin.savior;

import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.AssassinGuessRoleC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * External seam (pinned NoellesRoles {@code 1.7.6-h1.5.6-spark}): the Assassin guess receiver
 * {@code lambda$registerPackets$6} is dropped at HEAD when the packet names the Savior and the target really is one,
 * so the guess neither kills anyone nor consumes {@code canGuess}. The client panel already hides the role in
 * {@code AssassinSaviorGuessMixin}; this guards modified clients. Class-level {@code remap = false}. Server only.
 * 外部接缝（固定 NoellesRoles {@code 1.7.6-h1.5.6-spark}）：当猜测包点名救世主且目标确实是救世主时，在 HEAD 丢弃
 * 刺客猜测接收器 {@code lambda$registerPackets$6}，既不击杀任何人，也不消耗 {@code canGuess}。客户端面板已由
 * {@code AssassinSaviorGuessMixin} 隐藏该职业，这里防御改包客户端。类级 {@code remap = false}。仅服务端。
 */
@Mixin(value = Noellesroles.class, remap = false)
public abstract class NoellesAssassinSaviorGuessMixin {
    @Inject(
            method = "lambda$registerPackets$6(Lorg/agmas/noellesroles/packet/AssassinGuessRoleC2SPacket;"
                    + "Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private static void sparkwitch$ignoreSaviorGuess(
            AssassinGuessRoleC2SPacket packet,
            ServerPlayNetworking.Context context,
            CallbackInfo ci
    ) {
        if (!SaviorRules.ROLE_ID.equals(packet.guessedRole())) {
            return;
        }
        ServerPlayerEntity target = context.player().server.getPlayerManager().getPlayer(packet.targetPlayer());
        if (target == null) {
            return;
        }
        Role role = GameWorldComponent.KEY.get(target.getWorld()).getRole(target);
        if (SaviorRules.isSavior(role)) {
            ci.cancel();
        }
    }
}
