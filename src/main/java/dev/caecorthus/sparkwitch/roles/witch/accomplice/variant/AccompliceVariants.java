package dev.caecorthus.sparkwitch.roles.witch.accomplice.variant;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.Role;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registry of special accomplices ("accomplice variants"). Each registered role is a witch-faction accomplice that
 * Grand Witch recruitment may roll instead of the plain Accomplice, at most once per round, uniformly among the
 * enabled variants not yet used; the plain Accomplice is the fallback once the pool is exhausted. Every
 * "basic accomplice" rule reads {@code WitchFactionRules.isAccompliceLike}, which includes these roles.
 * Register during mod initialization, in each variant's own feature service; registration order is the roll order.
 * 特殊共犯注册表。每个已注册职业都是魔女阵营共犯：大魔女招募时可抽到它替代普通共犯，每局至多一次，在已启用且本局未出现的
 * 特殊共犯中均匀抽取；池子抽空后才是普通共犯。所有"共犯基础功能"规则都读取包含这些职业的
 * {@code WitchFactionRules.isAccompliceLike}。请在各特殊共犯自己的功能服务中于模组初始化时注册；注册顺序即抽取候选顺序。
 */
public final class AccompliceVariants {
    private static final List<Role> ROLES = new ArrayList<>();
    private static final Map<Role, AccompliceVariantHooks> HOOKS = new IdentityHashMap<>();

    private AccompliceVariants() {
    }

    /**
     * Registers a variant. The plain Accomplice and duplicates are rejected with {@link IllegalArgumentException}.
     * 注册特殊共犯；普通共犯本身与重复注册会抛出 {@link IllegalArgumentException}。
     */
    public static synchronized void register(Role role, AccompliceVariantHooks hooks) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(hooks, "hooks");
        if (role == SparkWitchRoles.accomplice()) {
            throw new IllegalArgumentException("The plain Accomplice is not a variant");
        }
        if (HOOKS.containsKey(role)) {
            throw new IllegalArgumentException("Duplicate accomplice variant: " + role.identifier());
        }
        ROLES.add(role);
        HOOKS.put(role, hooks);
    }

    /** True when the role is a registered special accomplice. / 是否为已注册的特殊共犯。 */
    public static synchronized boolean isVariant(Role role) {
        return role != null && HOOKS.containsKey(role);
    }

    /** Registered variants in registration order. / 按注册顺序列出的特殊共犯。 */
    public static synchronized List<Role> variants() {
        return List.copyOf(ROLES);
    }

    /** The variant's hooks, or {@link AccompliceVariantHooks#NONE}. / 该特殊共犯的回调，未注册时为 NONE。 */
    public static synchronized AccompliceVariantHooks hooks(Role role) {
        AccompliceVariantHooks hooks = role == null ? null : HOOKS.get(role);
        return hooks == null ? AccompliceVariantHooks.NONE : hooks;
    }
}
