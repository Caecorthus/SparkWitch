package dev.caecorthus.sparkwitch.roles.civilian.fisher;

import dev.caecorthus.sparkwitch.util.RoundParticipants;
import net.minecraft.entity.player.PlayerEntity;

/** Common eligibility for transferable fish and weapons. / 可转交鱼类与武器共用的参与资格。 */
public final class FisherParticipants {
    private FisherParticipants() {
    }

    /**
     * Shared by both sides; consuming fish never requires the Fisher role: alive, connected, playing and not a spectator
     * or creative player ({@link RoundParticipants#LIVING_SURVIVOR}).
     * 双端共用；使用鱼不要求钓鱼佬身份：存活、在线、参与且非旁观/创造（{@link RoundParticipants#LIVING_SURVIVOR}）。
     */
    public static boolean isLivingParticipant(PlayerEntity player) {
        return RoundParticipants.LIVING_SURVIVOR.test(player);
    }
}
