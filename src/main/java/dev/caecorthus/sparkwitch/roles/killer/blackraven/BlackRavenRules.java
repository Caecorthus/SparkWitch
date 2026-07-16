package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import java.util.UUID;
import net.minecraft.util.Identifier;

/**
 * Stable gameplay values and pure gates owned by Black Raven.
 * 黑羽鸦拥有的稳定玩法数值与纯规则判断。
 */
public final class BlackRavenRules {
    public static final Identifier ROLE_ID = SparkWitch.id("black_raven");
    public static final Identifier PERCEPTION_SKILL_ID = SparkWitch.id("perception");
    public static final int COLOR = 0x51445F;
    public static final double FEATHER_REACH = 3.0D;
    public static final int MARK_DURATION_TICKS = 20 * 20;
    public static final int FEATHER_COOLDOWN_TICKS = 60 * 20;
    public static final int PERCEPTION_INITIAL_COOLDOWN_TICKS = 60 * 20;
    public static final int PERCEPTION_ACTIVE_TICKS = 15 * 20;
    public static final int PERCEPTION_COOLDOWN_TICKS = 90 * 20;
    public static final double PERCEPTION_RADIUS_SQUARED = 8.0D * 8.0D;
    public static final int PERCEPTION_POINT_TICKS = 20;
    public static final int PERCEPTION_REVEAL_POINTS = 10;
    public static final int SHOP_PRICE = 75;

    private BlackRavenRules() {
    }

    public static boolean isBlackRaven(Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    public static boolean canMark(
            boolean exactRole,
            boolean markerAlive,
            boolean targetAlive,
            boolean self,
            boolean alreadyMarked,
            boolean visible,
            double squaredDistance
    ) {
        return exactRole
                && markerAlive
                && targetAlive
                && !self
                && !alreadyMarked
                && visible
                && squaredDistance <= FEATHER_REACH * FEATHER_REACH;
    }

    public static boolean isWithinPerceptionRadius(double squaredDistance) {
        return squaredDistance <= PERCEPTION_RADIUS_SQUARED;
    }

    /** Preserves disconnected knowledge only inside its original match and role. / 仅在原对局且仍为黑羽鸦时保留离线感知记录。 */
    public static boolean shouldPreservePerceptionRoundState(
            UUID storedMatchId,
            UUID currentMatchId,
            boolean exactRole
    ) {
        return exactRole && storedMatchId != null && storedMatchId.equals(currentMatchId);
    }
}
