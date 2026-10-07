package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeKillAttribution;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.bomber.BomberPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;

/** Exact saved bomb owner, including explosions inside Taotie. / 保存的炸弹责任人，包含饕餮腹内爆炸。 */
@Mixin(BomberPlayerComponent.class)
public abstract class JudgeBomberAttributionMixin {
    @Shadow public abstract UUID getBomberUuid();

    @WrapOperation(method = "explode", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/game/GameFunctions;killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;)V"), require = 2)
    private void sparkwitch$scopeBomb(ServerPlayerEntity victim, boolean body, ServerPlayerEntity killer,
                                     Identifier reason, Operation<Void> original) {
        JudgeKillAttribution.runWith(victim.getServerWorld(), getBomberUuid(),
                () -> original.call(victim, body, killer, reason));
    }
}
