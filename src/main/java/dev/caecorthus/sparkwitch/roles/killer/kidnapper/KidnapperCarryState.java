package dev.caecorthus.sparkwitch.roles.killer.kidnapper;

import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** Shared carried-corpse query for client gesture checks and server authority. / 客户端手势与服务端权威共用的携尸查询。 */
public final class KidnapperCarryState {
    private KidnapperCarryState() {
    }

    @Nullable
    public static PlayerBodyEntity findCarriedBody(Entity carrier) {
        for (Entity passenger : carrier.getPassengerList()) {
            if (passenger instanceof PlayerBodyEntity body) {
                return body;
            }
        }
        return null;
    }
}
