package dev.caecorthus.sparkwitch.compat;

import net.minecraft.server.world.ServerWorld;

import java.util.UUID;

/**
 * Weak bridge into the public SparkTraits API without a compile-time dependency.
 * 通过弱反射读取 SparkTraits 公共 API，避免编译期强依赖。
 */
public final class SparkTraitsLastStandBridge {
    private static final String SPARK_TRAITS_API_CLASS =
            "dev.caecorthus.sparktraits.api.SparkTraitsApi";

    // No mod check, as before: an absent SparkTraits is a missing class, which reads false like any other failure.
    // 与此前一致不检查模组：缺失 SparkTraits 即类缺失，与其他失败一样返回 false。
    private static final OptionalStaticMethod TRIGGERED = OptionalStaticMethod.ungated(SPARK_TRAITS_API_CLASS,
            "hasLastStandTriggeredThisRound", ServerWorld.class, UUID.class).initializingClass();

    private SparkTraitsLastStandBridge() {
    }

    public static boolean hasTriggeredThisRound(ServerWorld world, UUID playerUuid) {
        return world != null && playerUuid != null && TRIGGERED.callBoolean(false, false, false, world, playerUuid);
    }
}
