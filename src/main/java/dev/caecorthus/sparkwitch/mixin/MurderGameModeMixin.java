package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.registry.WitchRoleAssignmentService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.gamemode.MurderGameMode;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(MurderGameMode.class)
public abstract class MurderGameModeMixin {
    @Inject(
            method = "assignRolesAndGetKillerCount",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/cca/ScoreboardRoleSelectorComponent;assignCivilians(Lnet/minecraft/server/world/ServerWorld;Ldev/doctor4t/wathe/cca/GameWorldComponent;Ljava/util/List;)I"
            )
    )
    private static void sparkwitch$assignWitchesBeforeCivilians(
            @NotNull ServerWorld world,
            @NotNull List<ServerPlayerEntity> players,
            GameWorldComponent gameComponent,
            CallbackInfoReturnable<Integer> cir
    ) {
        WitchRoleAssignmentService.assignAfterNeutralsBeforeCivilians(world, gameComponent, players);
    }
}
