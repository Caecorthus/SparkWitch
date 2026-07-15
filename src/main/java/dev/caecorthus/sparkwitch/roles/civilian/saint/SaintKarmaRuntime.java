package dev.caecorthus.sparkwitch.roles.civilian.saint;

import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
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
        for (ServerPlayerEntity player : world.getPlayers()) {
            boolean marked = worldComponent.hasSaintKarma(player.getUuid());
            int remainingTicks = worldComponent.getSaintKarmaTicks(player.getUuid());
            if (remainingTicks > 0) {
                SaintKarmaCooldownService.apply(player, remainingTicks);
            }
            SaintKarmaService.updatePlayerMirror(player, marked, remainingTicks);
        }
        worldComponent.tickSaintKarmaState();
    }
}
