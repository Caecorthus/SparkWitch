package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Registers the Raven Form crow entity type ({@code sparkwitch:black_raven_crow}); runs from {@code SparkWitch} with the
 * other entity types. Never saved, never summonable, fire-immune, tracked every tick.
 * 注册化鸦乌鸦实体类型（{@code sparkwitch:black_raven_crow}）；与其他实体类型一同由 {@code SparkWitch} 调用。
 * 从不存盘、不可召唤、免疫火焰、每刻追踪。
 */
public final class BlackRavenCrowEntities {
    private static EntityType<BlackRavenCrowEntity> crow;
    private static boolean registered;

    private BlackRavenCrowEntities() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        crow = Registry.register(
                Registries.ENTITY_TYPE,
                BlackRavenCrowRules.ENTITY_ID,
                EntityType.Builder.<BlackRavenCrowEntity>create(BlackRavenCrowEntity::new, SpawnGroup.MISC)
                        .dimensions(BlackRavenCrowRules.CROW_WIDTH, BlackRavenCrowRules.CROW_HEIGHT)
                        .eyeHeight(BlackRavenCrowRules.CROW_EYE_HEIGHT)
                        .maxTrackingRange(BlackRavenCrowRules.CROW_TRACKING_RANGE)
                        .trackingTickInterval(BlackRavenCrowRules.CROW_TRACKING_INTERVAL)
                        .makeFireImmune()
                        .disableSaving()
                        .disableSummon()
                        .build(BlackRavenCrowRules.ENTITY_ID.toString())
        );
        registered = true;
    }

    public static EntityType<BlackRavenCrowEntity> crow() {
        if (crow == null) {
            throw new IllegalStateException("Black Raven crow entity is not registered yet");
        }
        return crow;
    }
}
