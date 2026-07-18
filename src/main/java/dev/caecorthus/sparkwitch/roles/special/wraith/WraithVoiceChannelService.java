package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.doctor4t.wathe.compat.TrainVoicePlugin;
import net.minecraft.server.network.ServerPlayerEntity;

/** Keeps active Wraiths in the living incoming-voice group. */
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
