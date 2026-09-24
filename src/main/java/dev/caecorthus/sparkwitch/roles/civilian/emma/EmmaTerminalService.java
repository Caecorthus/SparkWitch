package dev.caecorthus.sparkwitch.roles.civilian.emma;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Backlash is a terminal penalty, not an attack credited to the forbidden target.
 * 因子反噬是终结惩罚，不是归属于禁止目标的一次攻击。 */
public final class EmmaTerminalService {
    public static final Identifier DEATH_REASON = SparkWitch.id("factor_backlash");

    private EmmaTerminalService() { }

    public static boolean isBacklash(Identifier reason) {
        return DEATH_REASON.equals(reason);
    }

    public static void killFromBacklash(ServerPlayerEntity player) {
        if (GameWorldComponent.KEY.get(player.getWorld()).isRunning()
                && GameFunctions.isPlayerPlayingAndAlive(player)) {
            GameFunctions.killPlayer(player, true, null, DEATH_REASON, true);
        }
    }
}
