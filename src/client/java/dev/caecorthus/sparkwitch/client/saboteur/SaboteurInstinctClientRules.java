package dev.caecorthus.sparkwitch.client.saboteur;

import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurRole;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/** Extends Wathe's native killer-instinct gate only for the exact promoted Saboteur. / 仅为精确的晋升破坏者扩展 wathe 原生杀手本能门禁。 */
public final class SaboteurInstinctClientRules {
    private SaboteurInstinctClientRules() {
    }

    public static boolean shouldEnableNativeInstinct(
            boolean originalAllowed,
            @Nullable Identifier roleId,
            boolean confirmedServer,
            boolean activeWraith,
            boolean promotedWraith,
            boolean instinctKeyPressed
    ) {
        return originalAllowed
                || confirmedServer
                && isExtendedRole(roleId)
                && activeWraith
                && promotedWraith
                && instinctKeyPressed;
    }

    /**
     * The role half of {@link #shouldEnableNativeInstinct}: without {@code originalAllowed} no other role can pass it, so
     * callers may leave before the per-call key read. / {@link #shouldEnableNativeInstinct} 的职业部分：没有
     * {@code originalAllowed} 时其他职业无法通过，因此调用方可在逐次按键读取之前返回。
     */
    public static boolean isExtendedRole(@Nullable Identifier roleId) {
        return SaboteurRole.ID.equals(roleId);
    }
}
