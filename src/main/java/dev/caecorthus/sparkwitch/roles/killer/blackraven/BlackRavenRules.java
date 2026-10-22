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
    /** Owner 2026-10-21: Perception is usable from round start. / 所有者 2026-10-21：感知开局即可用。 */
    public static final int PERCEPTION_INITIAL_COOLDOWN_TICKS = 0;
    public static final int PERCEPTION_ACTIVE_TICKS = 15 * 20;
    public static final int PERCEPTION_COOLDOWN_TICKS = 90 * 20;
    public static final double PERCEPTION_RADIUS_SQUARED = 8.0D * 8.0D;
    /**
     * Held-instinct x-ray on perceived players (owner 2026-10-21): one raven colour, the ledger's feather-violet
     * {@code SHEEN}, measured from the camera entity. Never a role colour.
     * 按住本能键时对已感知玩家的透视（所有者 2026-10-21）：统一的鸦羽色（感知册的羽毛紫 {@code SHEEN}），以镜头实体为中心
     * 测距。绝不使用职业色。
     */
    public static final int XRAY_COLOR = 0x9D8BC2;
    public static final double XRAY_RADIUS_SQUARED = 8.0D * 8.0D;
    public static final int PERCEPTION_POINT_TICKS = 20;
    public static final int PERCEPTION_REVEAL_POINTS = 10;

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

    /** Inclusive 3D camera-to-target reach of the held-instinct x-ray. / 本能透视自镜头到目标的三维距离（含边界）。 */
    public static boolean isWithinXrayRadius(double squaredDistance) {
        return squaredDistance <= XRAY_RADIUS_SQUARED;
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
