package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.doctor4t.wathe.compat.TrainVoicePlugin;
import net.minecraft.server.network.ServerPlayerEntity;

/** Owns Wathe living/dead voice-group transitions for Wraith players. */
final class WraithVoiceChannelService {
    private WraithVoiceChannelService() {
    }

    static void restoreLivingChannel(ServerPlayerEntity player) {
        TrainVoicePlugin.resetPlayer(player.getUuid());
    }

    static void moveToDeadChannel(ServerPlayerEntity player) {
        TrainVoicePlugin.addPlayer(player.getUuid());
    }
}
