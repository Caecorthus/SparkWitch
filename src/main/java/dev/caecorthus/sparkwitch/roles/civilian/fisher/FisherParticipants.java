package dev.caecorthus.sparkwitch.roles.civilian.fisher;

import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/** Common eligibility for transferable fish and weapons. / 可转交鱼类与武器共用的参与资格。 */
public final class FisherParticipants {
    private FisherParticipants() {
    }

    /** Shared by both sides; consuming fish never requires the Fisher role. / 双端共用；使用鱼不要求钓鱼佬身份。 */
    public static boolean isLivingParticipant(PlayerEntity player) {
        return player != null && player.isAlive()
                && !(player instanceof ServerPlayerEntity serverPlayer && serverPlayer.isDisconnected())
                && GameFunctions.isPlayerPlayingAndAlive(player) && GameFunctions.isPlayerAliveAndSurvival(player);
    }
}
