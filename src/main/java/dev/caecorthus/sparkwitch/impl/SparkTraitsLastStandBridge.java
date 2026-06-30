package dev.caecorthus.sparkwitch.impl;

import net.minecraft.server.world.ServerWorld;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Weak bridge into SparkTraits Last Stand round state without a compile-time dependency.
 * 通过弱反射读取 SparkTraits 背水一战本局状态，避免编译期强依赖。
 */
public final class SparkTraitsLastStandBridge {
    private static final String LAST_STAND_SERVICE_CLASS = "dev.caecorthus.sparktraits.impl.LastStandService";

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
                Class<?> serviceClass = Class.forName(LAST_STAND_SERVICE_CLASS);
                hasTriggeredThisRoundMethod = serviceClass.getMethod(
                        "hasTriggeredThisRound",
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
