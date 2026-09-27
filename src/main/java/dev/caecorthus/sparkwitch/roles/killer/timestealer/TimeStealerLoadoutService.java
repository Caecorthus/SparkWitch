package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Clock loadout: grant on role assignment, per-tick restore into the hotbar for a living owner (plus the owner's stamp
 * upkeep), staggered stray sweep for everyone else, and removal on death or reset.
 * 时钟装备：职业分配时发放；存活持有者每 tick 恢复到快捷栏（并维护其邮票）；其他人错峰清理残留；死亡或重置时移除。
 */
public final class TimeStealerLoadoutService {
    private TimeStealerLoadoutService() {
    }

    public static void onRoleAssigned(ServerPlayerEntity player, @Nullable Role role) {
        // TODO(WP-03b): grant/strip the Clock; write the round-start cooldown only for a new role or match (N1).
    }

    /** Called every server tick for every player from {@link TimeStealerPlayerComponent}. / 由组件对每名玩家每 tick 调用。 */
    public static void tick(ServerPlayerEntity player) {
        // TODO(WP-03b): restore Clock + TimeStealerStampService.tickOwner for holders; 20-tick stray sweep otherwise.
    }

    /** Terminal death: removes the Clock and every stamp. / 终结死亡：移除时钟与所有邮票。 */
    public static void onDeath(ServerPlayerEntity player) {
        // TODO(WP-03b): remove Clocks and strip stamps (e.g. via TimeStealerStampService.reset).
    }

    public static void reset(ServerPlayerEntity player) {
        // TODO(WP-03b): remove Clocks from inventory, cursor and open handler slots.
    }
}
