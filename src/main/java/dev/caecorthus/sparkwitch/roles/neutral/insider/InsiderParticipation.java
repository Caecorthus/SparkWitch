package dev.caecorthus.sparkwitch.roles.neutral.insider;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Frozen Insider and Team Jiahao predicates, safe on client and server: they read only Wathe's synced game component.
 * Roles are exact-id gates, never inferred from faction or namespace.
 * 冻结的内应与嘉豪阵营判断，客户端与服务端均可调用：只读取 Wathe 已同步的对局组件。职业按精确 id 判断，
 * 从不由阵营或命名空间推断。
 */
public final class InsiderParticipation {
    private InsiderParticipation() {
    }

    public static boolean isInsiderRole(@Nullable Role role) {
        return role != null && InsiderRules.ROLE_ID.equals(role.identifier());
    }

    public static boolean isCorruptCopRole(@Nullable Role role) {
        return role != null && InsiderRules.CORRUPT_COP_ID.equals(role.identifier());
    }

    /** Team Jiahao: every Corrupt Cop and every Insider (D2). / 嘉豪阵营：所有黑警与所有内应（D2）。 */
    public static boolean isTeamRole(@Nullable Role role) {
        return isInsiderRole(role) || isCorruptCopRole(role);
    }

    public static boolean isInsider(@Nullable PlayerEntity player) {
        return player != null && isInsiderRole(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    public static boolean isCorruptCop(@Nullable PlayerEntity player) {
        return player != null && isCorruptCopRole(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    public static boolean isTeamMember(@Nullable PlayerEntity player) {
        return player != null && isTeamRole(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    /** Insider role, playing and alive in Wathe. / 拥有内应职业，并在 Wathe 中参与且存活。 */
    public static boolean isLivingInsider(@Nullable PlayerEntity player) {
        return isInsider(player) && GameFunctions.isPlayerPlayingAndAlive(player);
    }
}
