package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeRuntime;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.AssassinGuessRoleC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Suppress pre-kill success feedback only for a Judge-rejected correct guess; useGuess still runs.
 * 仅隐藏被法官拒绝的正确猜测成功提示；猜测次数、冷却及猜错自杀仍按原逻辑。 */
@Mixin(Noellesroles.class)
public abstract class JudgeAssassinOutcomeMixin {
    @WrapOperation(method = "lambda$registerPackets$6", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayerEntity;sendMessage(Lnet/minecraft/text/Text;Z)V", ordinal = 0))
    private static void sparkwitch$noFalseAssassinationSuccess(ServerPlayerEntity assassin, Text message, boolean overlay,
                                                              Operation<Void> original, AssassinGuessRoleC2SPacket packet,
                                                              ServerPlayNetworking.Context context) {
        if (!JudgeRuntime.blocksKill(assassin.getServerWorld(), assassin.getUuid(), packet.targetPlayer())) {
            original.call(assassin, message, overlay);
        }
    }
}
