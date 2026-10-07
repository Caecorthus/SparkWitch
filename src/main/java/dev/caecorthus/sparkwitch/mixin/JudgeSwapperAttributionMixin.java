package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeTrainFallAttribution;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.SwapperC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Set;

/** Capture successful actual Swapper teleports before Noelles writes a synthetic onGround flag.
 * 只记录实际成功的交换，在 Noelles 写入非真实 onGround 标志前探测落点。 */
@Mixin(Noellesroles.class)
public abstract class JudgeSwapperAttributionMixin {
    @WrapOperation(method = "lambda$registerPackets$4", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerEntity;teleport(Lnet/minecraft/server/world/ServerWorld;DDDLjava/util/Set;FF)Z"), require = 2)
    private static boolean sparkwitch$attributeSwap(PlayerEntity target, ServerWorld destination,
                                                    double x, double y, double z, Set<PositionFlag> flags,
                                                    float yaw, float pitch, Operation<Boolean> original,
                                                    SwapperC2SPacket payload, ServerPlayNetworking.Context context) {
        boolean teleported = original.call(target, destination, x, y, z, flags, yaw, pitch);
        if (teleported && target instanceof ServerPlayerEntity victim) {
            JudgeTrainFallAttribution.swapped(victim, context.player().getUuid());
        }
        return teleported;
    }
}
