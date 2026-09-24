package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaLifecycle;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.gamemode.MurderGameMode;
import java.util.List;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MurderGameMode.class, remap = false)
public abstract class EmmaRoundStartMixin {
    @Inject(method = "assignRolesAndGetKillerCount", at = @At("HEAD"))
    private static void sparkwitch$beginEmmaRound(ServerWorld world, List<ServerPlayerEntity> players,
                                                 GameWorldComponent game, CallbackInfoReturnable<Integer> cir) {
        EmmaLifecycle.beginRound(world);
    }
}
