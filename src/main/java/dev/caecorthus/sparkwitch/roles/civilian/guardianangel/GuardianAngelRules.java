package dev.caecorthus.sparkwitch.roles.civilian.guardianangel;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/** Pure Guardian Angel tuning and server-validation decisions. / 守护天使的纯数值与服务端校验决策。 */
public final class GuardianAngelRules {
    public static final Identifier ROLE_ID = Identifier.of("sparkwitch", "guardian_angel");
    public static final Identifier SKILL_ID = Identifier.of("sparkwitch", "guardian");
    public static final int COLOR = 0xF0D77A;
    public static final int INITIAL_COOLDOWN_TICKS = 20 * 60;
    public static final int POST_USE_COOLDOWN_TICKS = 20 * 90;
    public static final int SHIELD_DURATION_TICKS = 20 * 10;
    public static final double TARGET_RANGE = 3.0D;
    public static final double TARGET_RANGE_SQUARED = TARGET_RANGE * TARGET_RANGE;

    private static final Identifier ASSASSINATED = Identifier.of("noellesroles", "assassinated");
    private static final Identifier VOODOO = Identifier.of("noellesroles", "voodoo");

    private GuardianAngelRules() {
    }

    public static boolean isGuardianAngel(Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    public static boolean isGuardianAngel(PlayerEntity player) {
        if (player == null) {
            return false;
        }
        return isGuardianAngel(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    public static boolean canUse(
            boolean gameRunning,
            boolean activeWraith,
            boolean promotedWraith,
            Role role,
            int cooldownTicks
    ) {
        return gameRunning
                && activeWraith
                && promotedWraith
                && isGuardianAngel(role)
                && cooldownTicks <= 0;
    }

    public static boolean canTarget(
            boolean self,
            boolean targetAlive,
            boolean targetAlreadyShielded,
            boolean hasLineOfSight,
            double squaredDistance
    ) {
        return !self
                && targetAlive
                && !targetAlreadyShielded
                && hasLineOfSight
                && squaredDistance <= TARGET_RANGE_SQUARED;
    }

    /** Mirrors NoellesRoles Iron Man exclusions without loading its implementation classes. / 不加载其实现类并复刻铁人药剂的排除项。 */
    public static boolean shouldBlockDeath(Identifier deathReason) {
        return !GameConstants.DeathReasons.SHOT_INNOCENT.equals(deathReason)
                && !GameConstants.DeathReasons.FELL_OUT_OF_TRAIN.equals(deathReason)
                && !ASSASSINATED.equals(deathReason)
                && !VOODOO.equals(deathReason);
    }

    public static boolean shouldBlockWraithMicrophone(boolean activeWraith, Role role) {
        return activeWraith && !isGuardianAngel(role);
    }
}
