package dev.caecorthus.sparkwitch.impl;

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
        return componentBoolean(player, "isAllied");
    }

    public static boolean isShowdownActive(PlayerEntity player) {
        return componentBoolean(player, "isShowdownActive");
    }

    public static void activateShowdown(ServerWorld world, List<ServerPlayerEntity> boundShadowJesters) {
        if (world == null || boundShadowJesters == null || boundShadowJesters.isEmpty()) {
            return;
        }
        try {
            Class<?> componentClass = Class.forName(COMPONENT_CLASS);
            Method activate = componentClass.getMethod("activateShowdown", ServerWorld.class, List.class);
            activate.invoke(null, world, boundShadowJesters);
            GameRecordManager.recordGlobalEvent(world, SHADOW_SHOWDOWN_START, null, null);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
            // NoellesRoles 1.7.6 is optional at compile time; absence means there is no showdown to start.
            // NoellesRoles 1.7.6 在编译期是可选依赖；不存在时就没有需要启动的双影谢幕。
        }
    }

    private static boolean componentBoolean(PlayerEntity player, String methodName) {
        Object component = component(player);
        if (component == null) {
            return false;
        }
        try {
            Method method = component.getClass().getMethod(methodName);
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
        try {
            Class<?> componentClass = Class.forName(COMPONENT_CLASS);
            Field keyField = componentClass.getField("KEY");
            ComponentKey<?> key = (ComponentKey<?>) keyField.get(null);
            return key.get(player);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
            return null;
        }
    }
}
