package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeKillAttribution;
import dev.doctor4t.wathe.cca.PlayerPoisonComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;

/** Preserve poison/trap responsibility before reset or offline lookup loss. / 毒药与毒陷阱清理前保留责任 UUID。 */
@Mixin(PlayerPoisonComponent.class)
public abstract class JudgePoisonAttributionMixin {
    @Shadow public UUID poisoner;

    @WrapOperation(method = "serverTick", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/game/GameFunctions;killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;)V"))
    private void sparkwitch$scopePoison(ServerPlayerEntity victim, boolean body, ServerPlayerEntity killer,
                                       Identifier reason, Operation<Void> original) {
        JudgeKillAttribution.runWith(victim.getServerWorld(), poisoner,
                () -> original.call(victim, body, killer, reason));
    }
}
