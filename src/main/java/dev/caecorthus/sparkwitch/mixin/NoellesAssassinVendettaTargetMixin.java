package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.packet.AssassinGuessRoleC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Extends NoellesRoles' native Assassin target check only for the bound Vendetta pair. */
@Mixin(targets = "org.agmas.noellesroles.Noellesroles", remap = false)
public abstract class NoellesAssassinVendettaTargetMixin {
    @Redirect(
            method = {"lambda$registerPackets$6", "lambda$registerPackets$37"},
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/game/GameFunctions;isPlayerPlayingAndAlive(Lnet/minecraft/entity/player/PlayerEntity;)Z",
                    ordinal = 1
            ),
            require = 1
    )
    private static boolean sparkwitch$allowBoundVendettaTarget(
            PlayerEntity target,
            AssassinGuessRoleC2SPacket packet,
            ServerPlayNetworking.Context context
    ) {
        return GameFunctions.isPlayerPlayingAndAlive(target)
                || VendettaInteractionService.isBoundKillerTargetingVendetta(context.player(), target);
    }
}
