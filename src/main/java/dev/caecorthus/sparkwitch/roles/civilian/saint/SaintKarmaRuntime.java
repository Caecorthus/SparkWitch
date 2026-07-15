package dev.caecorthus.sparkwitch.roles.civilian.saint;

import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Advances world-owned Karma and continuously covers newly acquired inventory items.
 * 推进世界组件持有的业障计时，并让计时期间新获得的背包物品继承剩余冷却。
 */
public final class SaintKarmaRuntime {
    private SaintKarmaRuntime() {
    }

    public static void tick(ServerWorld world, WitchWorldComponent worldComponent) {
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(world);
        for (ServerPlayerEntity player : world.getPlayers()) {
            boolean marked = worldComponent.hasSaintKarma(player.getUuid());
            int remainingTicks = worldComponent.getSaintKarmaTicks(player.getUuid());
            Role role = gameComponent.getRole(player);
            int effectiveRemainingTicks = SaintRules.effectiveKarmaTicks(role, remainingTicks);
            if (effectiveRemainingTicks > 0) {
                SaintKarmaCooldownService.apply(player, effectiveRemainingTicks);
            }
            SaintKarmaService.updatePlayerMirror(player, marked, effectiveRemainingTicks);
        }
        worldComponent.tickSaintKarmaState();
    }
}
