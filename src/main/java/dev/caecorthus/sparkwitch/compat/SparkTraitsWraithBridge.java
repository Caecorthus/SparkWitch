package dev.caecorthus.sparkwitch.compat;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithLifecycleRules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional reflection bridge to the four public SparkTraits Wraith lifecycle methods.
 * 仅通过 SparkTraits 的四个公共冤魂生命周期方法提供可选反射桥接。
 */
public final class SparkTraitsWraithBridge {
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    private static final AtomicBoolean WARNING_LOGGED = new AtomicBoolean();
    private static volatile Resolution resolution;

    private SparkTraitsWraithBridge() {
    }

    public static NbtCompound capture(@Nullable PlayerEntity player) {
        Resolution resolved = resolve();
        if (!resolved.available()) {
            return new NbtCompound();
        }
        Object value = invoke(resolved.capture(), player);
        return value instanceof NbtCompound snapshot ? snapshot.copy() : new NbtCompound();
    }

    public static void restore(@Nullable PlayerEntity player, @Nullable NbtCompound snapshot) {
        Resolution resolved = resolve();
        if (!resolved.available() || player == null || snapshot == null) {
            return;
        }
        invoke(resolved.restore(), player, snapshot.copy());
    }

    public static void clear(@Nullable PlayerEntity player, boolean gameEnd) {
        Resolution resolved = resolve();
        if (!resolved.available() || player == null) {
            return;
        }
        invoke(resolved.clear(), player, gameEnd);
    }

    public static boolean hasLastStandTriggered(@Nullable ServerWorld world, @Nullable UUID playerUuid) {
        Resolution resolved = resolve();
        if (!resolved.available() || world == null || playerUuid == null) {
            return false;
        }
        return Boolean.TRUE.equals(invoke(resolved.lastStand(), world, playerUuid));
    }

    public static boolean didLastStandTriggerSince(@Nullable PlayerEntity player, boolean triggeredBefore) {
        return player != null && WraithLifecycleRules.didNewLastStandTrigger(
                triggeredBefore,
                hasLastStandTriggered((ServerWorld) player.getWorld(), player.getUuid())
        );
    }

    private static Resolution resolve() {
        Resolution current = resolution;
        if (current != null) {
            return current;
        }
        synchronized (SparkTraitsWraithBridge.class) {
            if (resolution != null) {
                return resolution;
            }
            try {
                Class<?> api = Class.forName(API_CLASS);
                resolution = new Resolution(
                        api.getMethod("captureWraithTraitSnapshot", PlayerEntity.class),
                        api.getMethod("restoreWraithTraitSnapshot", PlayerEntity.class, NbtCompound.class),
                        api.getMethod("clearWraithTraits", PlayerEntity.class, boolean.class),
                        api.getMethod("hasLastStandTriggeredThisRound", ServerWorld.class, UUID.class)
                );
            } catch (ReflectiveOperationException | LinkageError error) {
                warnOnce(error);
                resolution = Resolution.UNAVAILABLE;
            }
            return resolution;
        }
    }

    private static @Nullable Object invoke(@Nullable Method method, Object... arguments) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(null, arguments);
        } catch (IllegalAccessException | InvocationTargetException | LinkageError error) {
            warnOnce(error);
            return null;
        }
    }

    private static void warnOnce(Throwable error) {
        if (WARNING_LOGGED.compareAndSet(false, true)) {
            SparkWitch.LOGGER.warn(
                    "SparkTraits Wraith compatibility is unavailable; continuing without trait restoration ({})",
                    error.getClass().getSimpleName()
            );
        }
    }

    private record Resolution(
            @Nullable Method capture,
            @Nullable Method restore,
            @Nullable Method clear,
            @Nullable Method lastStand
    ) {
        private static final Resolution UNAVAILABLE = new Resolution(null, null, null, null);

        private boolean available() {
            return capture != null && restore != null && clear != null && lastStand != null;
        }
    }
}
