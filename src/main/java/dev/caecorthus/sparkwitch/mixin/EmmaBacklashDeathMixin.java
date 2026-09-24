package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaTerminalService;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Terminal backlash does not spend shields or approve a revival that cannot prevent the death.
 * 终结反噬不消耗无效护盾，也不批准无法阻止此次死亡的复起。 */
@Mixin(value = GameFunctions.class, remap = false)
public abstract class EmmaBacklashDeathMixin {
    @WrapOperation(method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/api/event/KillPlayer$Before;beforeKillPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;)Ldev/doctor4t/wathe/api/event/KillPlayer$KillResult;"))
    private static KillPlayer.KillResult sparkwitch$terminalBacklash(KillPlayer.Before listener,
            ServerPlayerEntity victim, ServerPlayerEntity killer, Identifier reason,
            Operation<KillPlayer.KillResult> original) {
        return killer == null && EmmaTerminalService.isBacklash(reason)
                ? null : original.call(listener, victim, killer, reason);
    }
}
