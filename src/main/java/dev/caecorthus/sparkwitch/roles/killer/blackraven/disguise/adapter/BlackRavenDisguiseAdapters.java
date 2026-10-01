package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseRules;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Registry of shipped disguise adapters, filled once during common init on both sides.
 * A role is shipped only when it is allowed and has an adapter; everything else fails closed.
 * 已上线伪装适配器的注册表，在双端通用初始化时一次性填充。
 * 仅当职业被允许且拥有适配器时才算上线；其余一律失败关闭。
 */
public final class BlackRavenDisguiseAdapters {
    private static final Map<Identifier, BlackRavenDisguiseAdapter> ADAPTERS = new ConcurrentHashMap<>();

    private BlackRavenDisguiseAdapters() {
    }

    public static synchronized void register(BlackRavenDisguiseAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter");
        Identifier id = Objects.requireNonNull(adapter.roleId(), "roleId");
        if (!BlackRavenDisguiseRules.isAllowed(id)) {
            throw new IllegalArgumentException("Black Raven disguise role is not allowed: " + id);
        }
        if (ADAPTERS.putIfAbsent(id, adapter) != null) {
            throw new IllegalStateException("Duplicate Black Raven disguise adapter: " + id);
        }
    }

    public static @Nullable BlackRavenDisguiseAdapter get(@Nullable Identifier roleId) {
        return roleId == null ? null : ADAPTERS.get(roleId);
    }

    public static boolean isShipped(@Nullable Identifier roleId) {
        return BlackRavenDisguiseRules.isAllowed(roleId) && ADAPTERS.containsKey(roleId);
    }

    public static Collection<BlackRavenDisguiseAdapter> all() {
        return List.copyOf(ADAPTERS.values());
    }
}
