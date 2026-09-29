package dev.caecorthus.sparkwitch.roles.civilian.fisher;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;

final class FisherTargeting {
    private FisherTargeting() {
    }

    /** Shared by both sides; consuming fish never requires the Fisher role. / 双端共用；使用鱼不要求钓鱼佬身份。 */
    static boolean isLivingParticipant(PlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return game.isRunning() && game.hasAnyRole(player) && !game.isPlayerDead(player.getUuid())
                && player.isAlive() && !player.isSpectator();
    }
}
