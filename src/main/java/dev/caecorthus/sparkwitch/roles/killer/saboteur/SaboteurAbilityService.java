package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import dev.caecorthus.sparkwitch.record.AchievementRecords;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;

/** Server-authoritative Sabotage activation. / 服务端权威的破坏技能入口。 */
public final class SaboteurAbilityService {
    private SaboteurAbilityService() {
    }

    public static boolean use(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        SaboteurPlayerComponent component = SaboteurPlayerComponent.KEY.get(player);
        int[] lamps = new int[1];
        boolean used = SaboteurAbilityRuntime.use(
                game.isRunning(),
                SaboteurRules.isActivePromotedSaboteur(player),
                component.isReady(),
                () -> lamps[0] = SaboteurLightOutageService.activate(player),
                component::setCooldownTicks
        );
        if (used) {
            // Achievement record W3, after the outage and cooldown. / 成就记录 W3，在熄灯与冷却之后写入。
            AchievementRecords.sabotage(player, lamps[0]);
        }
        return used;
    }
}
