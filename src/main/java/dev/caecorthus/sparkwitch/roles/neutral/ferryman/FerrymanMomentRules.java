package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Pure gates of the Soul Moment: whether it may start and how many players are still outside it.
 * 亡魂时刻的纯判定：能否开始，以及时刻之外还有多少存活者。
 */
public final class FerrymanMomentRules {
    private FerrymanMomentRules() {
    }

    public static boolean canStart(boolean active, int eligibleSouls) {
        return !active && eligibleSouls > 0;
    }

    /** Living players that are neither the owner nor a Lost Soul. / 既不是拥有者也不是迷失灵魂的存活玩家。 */
    public static int outsidersAlive(ServerWorld world, FerrymanMomentWorldComponent moment) {
        int alive = 0;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!GameFunctions.isPlayerPlayingAndAlive(player)) {
                continue;
            }
            if (moment.owns(player.getUuid()) || moment.isSoul(player.getUuid())) {
                continue;
            }
            alive++;
        }
        return alive;
    }
}
