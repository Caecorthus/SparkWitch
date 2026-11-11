package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The Ferryman's single participation predicate, shared by every service so no caller re-derives the role check.
 * 摆渡人的唯一参战判定，所有服务共用，调用方不再自行推导职业判断。
 */
public final class FerrymanParticipation {
    private FerrymanParticipation() {
    }

    public static boolean isFerryman(@Nullable Role role) {
        return FerrymanRules.isFerryman(role);
    }

    /** The player's raw Wathe role is the Ferryman (a disguise overlay never widens this). / 玩家的真实 Wathe 职业是摆渡人。 */
    public static boolean isFerryman(PlayerEntity player) {
        return isFerryman(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    /** Exact role and still playing and alive. / 精确职业且仍在局内存活。 */
    public static boolean isActiveFerryman(ServerPlayerEntity player) {
        return isFerryman(player) && GameFunctions.isPlayerPlayingAndAlive(player);
    }
}
