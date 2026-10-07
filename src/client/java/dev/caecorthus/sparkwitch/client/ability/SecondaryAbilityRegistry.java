package dev.caecorthus.sparkwitch.client.ability;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Client-only role dispatcher; concrete roles own every secondary-key behavior. / 仅客户端角色分发器；第二技能键行为全部由具体角色拥有。 */
public final class SecondaryAbilityRegistry {
    private static final Map<Identifier, SecondaryAbilityHandler> HANDLERS = new LinkedHashMap<>();

    private SecondaryAbilityRegistry() {
    }

    public static synchronized void register(Identifier roleId, SecondaryAbilityHandler handler) {
        if (!tryRegister(roleId, handler)) {
            throw new IllegalStateException("Secondary ability handler already registered for " + roleId);
        }
    }

    /**
     * Non-throwing form for the public {@code api.client.SecondarySkillKeyApi}: false keeps the first handler.
     * 供公共 {@code api.client.SecondarySkillKeyApi} 使用的不抛异常形式：返回 false 时保留先注册的处理器。
     */
    public static synchronized boolean tryRegister(Identifier roleId, SecondaryAbilityHandler handler) {
        Objects.requireNonNull(roleId, "roleId");
        Objects.requireNonNull(handler, "handler");
        return HANDLERS.putIfAbsent(roleId, handler) == null;
    }

    public static synchronized @Nullable SecondaryAbilityHandler get(Identifier roleId) {
        return HANDLERS.get(roleId);
    }

    static synchronized void resetAll() {
        HANDLERS.values().forEach(SecondaryAbilityHandler::reset);
    }
}
