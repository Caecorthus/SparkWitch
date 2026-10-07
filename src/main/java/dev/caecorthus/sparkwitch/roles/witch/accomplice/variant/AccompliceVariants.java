package dev.caecorthus.sparkwitch.roles.witch.accomplice.variant;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.Role;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Registry of special accomplices ("accomplice variants"). Each registered role is a witch-faction accomplice that a
 * Bewitched promotion may roll instead of the plain Accomplice, at most once per round, uniformly among the
 * enabled variants not yet used; the plain Accomplice is the fallback once the pool is exhausted. Every
 * "basic accomplice" rule reads {@code WitchFactionRules.isAccompliceLike}, which includes these roles.
 * Register during mod initialization, in each variant's own feature service; registration order is the roll order.
 * 特殊共犯注册表。每个已注册职业都是魔女阵营共犯：魔化使晋升时可抽到它替代普通共犯，每局至多一次，在已启用且本局未出现的
 * 特殊共犯中均匀抽取；池子抽空后才是普通共犯。所有"共犯基础功能"规则都读取包含这些职业的
 * {@code WitchFactionRules.isAccompliceLike}。请在各特殊共犯自己的功能服务中于模组初始化时注册；注册顺序即抽取候选顺序。
 */
public final class AccompliceVariants {
    /**
     * Lock-free reads: {@link #isVariant}, {@link #variants} and {@link #hooks} run per frame on the client (the skills
     * panel gate reads the hooks), so they read one immutable snapshot that the synchronized {@link #register} replaces
     * wholesale (the volatile write publishes it).
     * 无锁读取：isVariant、variants 与 hooks 在客户端每帧运行（技能面板门禁读取 hooks），因此读取同一个不可变快照，
     * 由同步的 register 整体替换（volatile 写入负责发布）。
     */
    private static volatile Snapshot snapshot = new Snapshot(List.of(), Set.of(), Map.of());

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
        if (snapshot.members().contains(role)) {
            throw new IllegalArgumentException("Duplicate accomplice variant: " + role.identifier());
        }
        List<Role> roles = new ArrayList<>(snapshot.roles());
        roles.add(role);
        Set<Role> members = Collections.newSetFromMap(new IdentityHashMap<>());
        members.addAll(roles);
        Map<Role, AccompliceVariantHooks> hooksByRole = new IdentityHashMap<>(snapshot.hooks());
        hooksByRole.put(role, hooks);
        snapshot = new Snapshot(List.copyOf(roles), Collections.unmodifiableSet(members),
                Collections.unmodifiableMap(hooksByRole));
    }

    /** True when the role is a registered special accomplice. / 是否为已注册的特殊共犯。 */
    public static boolean isVariant(Role role) {
        return role != null && snapshot.members().contains(role);
    }

    /** Registered variants in registration order, as an immutable list. / 按注册顺序列出的特殊共犯（不可变列表）。 */
    public static List<Role> variants() {
        return snapshot.roles();
    }

    /** The variant's hooks, or {@link AccompliceVariantHooks#NONE}. / 该特殊共犯的回调，未注册时为 NONE。 */
    public static AccompliceVariantHooks hooks(Role role) {
        AccompliceVariantHooks hooks = role == null ? null : snapshot.hooks().get(role);
        return hooks == null ? AccompliceVariantHooks.NONE : hooks;
    }

    /**
     * Registered roles in order, an identity set of them, and their hooks by identity.
     * 按顺序的已注册职业、其身份集合，以及按身份索引的回调。
     */
    private record Snapshot(List<Role> roles, Set<Role> members, Map<Role, AccompliceVariantHooks> hooks) {
    }
}
