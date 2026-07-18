package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.compat.TrainVoicePlugin;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps active Wraith game mode and voice routing out of Wathe's ordinary dead-player branch. */
@Mixin(GameWorldComponent.class)
public abstract class WraithDeadParticipationMixin {
    @Shadow @Final private World world;

    @Redirect(
            method = "serverTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;changeGameMode(Lnet/minecraft/world/GameMode;)Z"
            ),
            require = 1
    )
    private boolean sparkwitch$keepActiveWraithGameMode(
            ServerPlayerEntity player,
            GameMode gameMode
    ) {
        return WraithStateService.isActive(player) || player.changeGameMode(gameMode);
    }

    @Redirect(
            method = "serverTick",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/compat/TrainVoicePlugin;addPlayer(Ljava/util/UUID;)V"
            ),
            require = 1
    )
    private void sparkwitch$keepActiveWraithVoice(java.util.UUID playerUuid) {
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerUuid);
        if (player == null || !WraithStateService.isActive(player)) {
            TrainVoicePlugin.addPlayer(playerUuid);
        }
    }
}
