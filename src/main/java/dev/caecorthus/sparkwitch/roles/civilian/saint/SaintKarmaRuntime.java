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
            if (!marked && isMirrorClear(player)) {
                // Both branches below are no-ops here: an unmarked player has no Karma entry (nor an admin exemption,
                // which only exists while marked), and updatePlayerMirror(false, 0) leaves a clear mirror unchanged.
                // 此时下面两个分支都是空操作：未标记玩家没有业障条目（管理员豁免也只在标记期间存在），
                // updatePlayerMirror(false, 0) 不会改变已清空的镜像。
                continue;
            }
            Role role = gameComponent.getRole(player);
            if (SaintRules.isKarmaImmune(role)) {
                SaintKarmaService.clear(player);
                continue;
            }

            int remainingTicks = worldComponent.getSaintKarmaTicks(player.getUuid());
            if (remainingTicks > 0) {
                SaintKarmaCooldownService.apply(player, remainingTicks);
            }
            SaintKarmaService.updatePlayerMirror(player, marked, remainingTicks);
        }
        worldComponent.tickSaintKarmaState();
    }

    /** The player's Karma mirror shows neither a mark nor a cooldown. / 玩家的业障镜像既无标记也无冷却。 */
    private static boolean isMirrorClear(ServerPlayerEntity player) {
        SaintPlayerState mirror = WitchPlayerComponent.KEY.get(player).getSaintState();
        return !mirror.isKarmaMarked() && mirror.karmaCooldownTicks() == 0;
    }
}
