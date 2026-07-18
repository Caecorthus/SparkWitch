package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;

/** Server-authoritative Sabotage activation. / 服务端权威的破坏技能入口。 */
public final class SaboteurAbilityService {
    private SaboteurAbilityService() {
    }

    public static boolean use(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        if (!game.isRunning() || !SaboteurRules.isActivePromotedSaboteur(player)) {
            return false;
        }
        SaboteurPlayerComponent component = SaboteurPlayerComponent.KEY.get(player);
        if (!component.isReady()) {
            return false;
        }

        // An empty capture is deliberately indistinguishable from one containing eligible lamps.
        // 空范围也必须与存在合格灯具时一样成功，不能泄露附近灯具信息。
        SaboteurLightOutageService.activate(player);
        component.setCooldownTicks(SaboteurRules.COOLDOWN_TICKS);
        return true;
    }
}
