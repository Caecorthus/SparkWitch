package dev.caecorthus.sparkwitch.compat;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Weak bridge into the public SparkTraits API without a compile-time dependency.
 * 通过弱反射读取 SparkTraits 公共 API，避免编译期强依赖。
 */
public final class SparkTraitsLastStandBridge {
    private static final String SPARK_TRAITS_API_CLASS =
            "dev.caecorthus.sparktraits.api.SparkTraitsApi";

    private static volatile Method hasTriggeredThisRoundMethod;
    private static volatile boolean lookupFailed;

    private SparkTraitsLastStandBridge() {
    }

    public static boolean hasTriggeredThisRound(ServerWorld world, UUID playerUuid) {
        if (world == null || playerUuid == null) {
            return false;
        }
        Method method = hasTriggeredThisRoundMethod();
        if (method == null) {
            return false;
        }
        try {
            Object result = method.invoke(null, world, playerUuid);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
            return false;
        }
    }

    /** A Loose End made by Last Stand's Final Moment: the same role + triggered pair SparkTraits checks.
     * 背水一战终局时刻转成的亡命徒：与 SparkTraits 相同的“亡命徒身份 + 本局已触发背水一战”判定。 */
    public static boolean isLastStandLooseEnd(ServerWorld world, UUID playerUuid, @Nullable Role role) {
        return role != null
                && WatheRoles.LOOSE_END.identifier().equals(role.identifier())
                && hasTriggeredThisRound(world, playerUuid);
    }

    private static Method hasTriggeredThisRoundMethod() {
        if (lookupFailed) {
            return null;
        }
        Method cached = hasTriggeredThisRoundMethod;
        if (cached != null) {
            return cached;
        }
        synchronized (SparkTraitsLastStandBridge.class) {
            if (hasTriggeredThisRoundMethod != null) {
                return hasTriggeredThisRoundMethod;
            }
            if (lookupFailed) {
                return null;
            }
            try {
                Class<?> apiClass = Class.forName(SPARK_TRAITS_API_CLASS);
                hasTriggeredThisRoundMethod = apiClass.getMethod(
                        "hasLastStandTriggeredThisRound",
                        ServerWorld.class,
                        UUID.class
                );
                return hasTriggeredThisRoundMethod;
            } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
                lookupFailed = true;
                return null;
            }
        }
    }
}
