package dev.caecorthus.sparkwitch.compat;

import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.ladysnake.cca.api.v3.component.ComponentKey;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Weak bridge into NoellesRoles Shadow Jester state without a compile-time dependency.
 * 通过弱反射桥接 NoellesRoles 双影小丑状态，避免编译期强依赖。
 */
public final class ShadowJesterShowdownBridge {
    private static final String COMPONENT_CLASS = "org.agmas.noellesroles.shadowjester.ShadowJesterPlayerComponent";
    private static final Identifier SHADOW_SHOWDOWN_START = Identifier.of(NoellesRoleIds.NAMESPACE, "shadow_showdown_start");

    private ShadowJesterShowdownBridge() {
    }

    public static boolean isAllied(PlayerEntity player) {
        return componentBoolean(player, Accessors.IS_ALLIED);
    }

    public static boolean isShowdownActive(PlayerEntity player) {
        return componentBoolean(player, Accessors.IS_SHOWDOWN_ACTIVE);
    }

    public static void activateShowdown(ServerWorld world, List<ServerPlayerEntity> boundShadowJesters) {
        if (world == null || boundShadowJesters == null || boundShadowJesters.isEmpty()) {
            return;
        }
        Method activate = Holder.ACTIVATE;
        if (activate == null) {
            // NoellesRoles 1.7.6 is optional at compile time; absence means there is no showdown to start.
            // NoellesRoles 1.7.6 在编译期是可选依赖；不存在时就没有需要启动的双影谢幕。
            return;
        }
        try {
            activate.invoke(null, world, boundShadowJesters);
            GameRecordManager.recordGlobalEvent(world, SHADOW_SHOWDOWN_START, null, null);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
            // A failing showdown start records nothing, as before. / 谢幕启动失败时与此前一样不记录。
        }
    }

    private static boolean componentBoolean(PlayerEntity player, Accessor accessor) {
        Object component = component(player);
        if (component == null) {
            return false;
        }
        try {
            Method method = accessor.resolve(component.getClass());
            if (method == null) {
                return false;
            }
            Object result = method.invoke(component);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
            return false;
        }
    }

    private static Object component(PlayerEntity player) {
        if (player == null) {
            return null;
        }
        ComponentKey<?> key = Holder.KEY;
        // An unresolvable component class or key fails closed exactly as the per-call lookup did.
        // 无法解析的组件类或键与逐次查找时一样失败即关闭。
        return key == null ? null : key.get(player);
    }

    /**
     * Resolved once on first use (class init is thread-safe): the NoellesRoles class, its {@code KEY} and its static
     * {@code activateShowdown} never change at runtime, so a failed lookup is latched as null (fail closed).
     * 首次使用时解析一次（类初始化线程安全）：NoellesRoles 类、其 {@code KEY} 与静态 {@code activateShowdown} 在运行期
     * 不会改变，因此查找失败锁定为 null（失败即关闭）。
     */
    private static final class Holder {
        private static final Class<?> COMPONENT = componentClass();
        private static final ComponentKey<?> KEY = key();
        private static final Method ACTIVATE = activate();

        private static Class<?> componentClass() {
            try {
                return Class.forName(COMPONENT_CLASS);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }

        private static ComponentKey<?> key() {
            if (COMPONENT == null) {
                return null;
            }
            try {
                Field keyField = COMPONENT.getField("KEY");
                return (ComponentKey<?>) keyField.get(null);
            } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
                return null;
            }
        }

        private static Method activate() {
            if (COMPONENT == null) {
                return null;
            }
            try {
                return COMPONENT.getMethod("activateShowdown", ServerWorld.class, List.class);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }
    }

    private static final class Accessors {
        private static final Accessor IS_ALLIED = new Accessor("isAllied");
        private static final Accessor IS_SHOWDOWN_ACTIVE = new Accessor("isShowdownActive");
    }

    /**
     * A no-argument public method of the component's runtime class, cached per class; a missing method is cached as
     * null too, since a class's public methods never change.
     * 组件运行时类上的无参 public 方法，按类缓存；类的 public 方法不会改变，因此缺失也缓存为 null。
     */
    private static final class Accessor {
        private final String name;
        private volatile Resolved resolved;

        private Accessor(String name) {
            this.name = name;
        }

        private Method resolve(Class<?> type) {
            Resolved current = resolved;
            if (current == null || current.type() != type) {
                Method method;
                try {
                    method = type.getMethod(name);
                } catch (NoSuchMethodException ignored) {
                    method = null;
                }
                current = new Resolved(type, method);
                resolved = current;
            }
            return current.method();
        }
    }

    private record Resolved(Class<?> type, Method method) {
    }
}
