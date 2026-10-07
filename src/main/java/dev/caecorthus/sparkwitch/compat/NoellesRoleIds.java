package dev.caecorthus.sparkwitch.compat;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Weak role-id bridge for narrow NoellesRoles compatibility that remains owned by SparkWitch.
 * SparkWitch 保留的 NoellesRoles 弱身份桥，只覆盖明确的跨模组交互。
 */
public final class NoellesRoleIds {
    public static final String NAMESPACE = "noellesroles";
    public static final Identifier PHANTOM = Identifier.of(NAMESPACE, "phantom");
    public static final Identifier SHADOW_JESTER = Identifier.of(NAMESPACE, "shadow_jester");
    public static final Identifier UNDERCOVER = Identifier.of(NAMESPACE, "undercover");
    /**
     * NoellesRoles Timekeeper. The stable signal is this id: {@code Noellesroles.TIMEKEEPER} is public but not
     * final, and SparkStrength keys on the same id; NoellesRoles registers exactly one role for it.
     * NoellesRoles 计时员。稳定信号是该 id：{@code Noellesroles.TIMEKEEPER} 虽为 public 但非 final，
     * SparkStrength 也按同一 id 识别；NoellesRoles 只为该 id 注册一个职业。
     */
    public static final Identifier TIMEKEEPER = Identifier.of(NAMESPACE, "time_keeper");
    public static final Identifier VOODOO_CURSE_DEATH_REASON = Identifier.of(NAMESPACE, "voodoo");

    private NoellesRoleIds() {
    }

    public static boolean isShadowJester(@Nullable Role role) {
        return hasId(role, SHADOW_JESTER);
    }

    public static boolean isPhantom(@Nullable Role role) {
        return hasId(role, PHANTOM);
    }

    public static boolean isUndercover(@Nullable Role role) {
        return hasId(role, UNDERCOVER);
    }

    /** Exact current-role check by id only; faction is a separate gate. / 仅按 id 精确判断当前职业；阵营是另一道判定。 */
    public static boolean isTimekeeper(@Nullable Role role) {
        return hasId(role, TIMEKEEPER);
    }

    public static boolean hasId(@Nullable Role role, Identifier id) {
        return role != null && id.equals(role.identifier());
    }
}
