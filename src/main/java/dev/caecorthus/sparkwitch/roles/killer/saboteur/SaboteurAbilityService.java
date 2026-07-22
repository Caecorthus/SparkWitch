package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;

/** Server-authoritative Sabotage activation. / 服务端权威的破坏技能入口。 */
public final class SaboteurAbilityService {
    private SaboteurAbilityService() {
    }

    public static boolean use(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        SaboteurPlayerComponent component = SaboteurPlayerComponent.KEY.get(player);
        return SaboteurAbilityRuntime.use(
                game.isRunning(),
                SaboteurRules.isActivePromotedSaboteur(player),
                component.isReady(),
                () -> SaboteurLightOutageService.activate(player),
                component::setCooldownTicks
        );
    }
}
