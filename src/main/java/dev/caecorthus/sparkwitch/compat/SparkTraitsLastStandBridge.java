package dev.caecorthus.sparkwitch.compat;

import net.minecraft.server.world.ServerWorld;

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
