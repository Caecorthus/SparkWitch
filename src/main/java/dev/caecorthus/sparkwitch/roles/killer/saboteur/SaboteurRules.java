package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Pure Saboteur values plus the promoted-Wraith identity boundary.
 * 破坏者纯规则，以及精确的冤魂晋升身份边界。
 */
public final class SaboteurRules {
    public static final Identifier ROLE_ID = SaboteurRole.ID;
    public static final int TASK_REWARD = 50;
    public static final int LOCKPICK_PRICE = 50;
    public static final int LIGHT_RADIUS = 20;
    public static final int LIGHT_DURATION_TICKS = 400;
    public static final int INITIAL_COOLDOWN_TICKS = 1_200;
    public static final int COOLDOWN_TICKS = 2_400;

    private SaboteurRules() {
    }

    public static boolean isSaboteur(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    public static boolean isSaboteur(@Nullable PlayerEntity player) {
        return player != null
                && isSaboteur(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    public static boolean isActivePromotedSaboteur(@Nullable PlayerEntity player) {
        if (!isSaboteur(player)) {
            return false;
        }
        WraithPlayerComponent wraith = WraithPlayerComponent.KEY.get(player);
        return isActivePromotedSaboteur(true, wraith.isActive(), wraith.isPromoted());
    }

    static boolean isActivePromotedSaboteur(
            boolean saboteur,
            boolean activeWraith,
            boolean promotedWraith
    ) {
        return saboteur && activeWraith && promotedWraith;
    }

    public static boolean canPassShopAliveGate(boolean ordinarilyAllowed, boolean activePromotedSaboteur) {
        return ordinarilyAllowed || activePromotedSaboteur;
    }

    static boolean shouldRewardTask(boolean activePromotedSaboteur) {
        return activePromotedSaboteur;
    }
}
