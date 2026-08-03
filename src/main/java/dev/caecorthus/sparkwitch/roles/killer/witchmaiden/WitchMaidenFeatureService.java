package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/** Owns Witch Maiden's final Voodoo cancellation. / 负责巫女的巫毒最终死亡取消。 */
public final class WitchMaidenFeatureService {
    private static boolean registered;

    private WitchMaidenFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        KillPlayer.BEFORE.register(WitchMaidenFeatureService::beforeKill);
    }

    private static @Nullable KillPlayer.KillResult beforeKill(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(victim.getServerWorld());
        if (!WitchMaidenRules.blocksVoodooDeath(gameComponent.getRole(victim), deathReason)) {
            return null;
        }
        return KillPlayer.KillResult.cancel();
    }
}
