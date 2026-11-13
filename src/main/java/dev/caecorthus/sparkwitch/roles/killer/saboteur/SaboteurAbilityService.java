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
        // Exactly ACTIVE (owner 2026-11): not the post-win STOPPING fade, which Wathe still calls running.
        // 必须恰为 ACTIVE（所有者 2026-11）：不包括胜负已定后的 STOPPING 淡出，Wathe 仍将其视为进行中。
        boolean used = SaboteurAbilityRuntime.use(
                game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
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
