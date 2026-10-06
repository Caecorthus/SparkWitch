package dev.caecorthus.sparkwitch.roles.civilian.apprentice;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Who Fear no longer blocks (owner D4/D8): the Apprentice Witch herself, and anyone warded by her Healing aura. Read on
 * both sides through {@code GrandWitchFearService.isPlayerFeared}; the ward is owner-synced, so a client only ever asks
 * about its own player. Fear's sanity drain is role-based and still applies.
 * 恐惧不再封锁的玩家（所有者 D4/D8）：预备魔女本人，以及受其疗愈光环庇护的玩家。经
 * {@code GrandWitchFearService.isPlayerFeared} 在两端读取；庇护只同步给本人，因此客户端只会查询自己。恐惧的理智扣除按职业判断，
 * 仍然生效。
 */
public final class ApprenticeFearExemption {
    private ApprenticeFearExemption() {
    }

    public static boolean isExempt(PlayerEntity player) {
        if (player == null) {
            return false;
        }
        return GameWorldComponent.KEY.get(player.getWorld()).getRole(player) == SparkWitchRoles.apprenticeWitch()
                || ApprenticePlayerComponent.KEY.get(player).hasFearWard();
    }
}
