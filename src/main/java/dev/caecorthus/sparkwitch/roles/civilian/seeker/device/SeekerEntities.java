package dev.caecorthus.sparkwitch.roles.civilian.seeker.device;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Registers the Seeker device entity types; must run before SparkWitchItems. Devices never save to disk and cannot be
 * summoned; every one is swept at round end.
 * 注册搜寻者设备实体类型；必须先于 SparkWitchItems 执行。设备从不存盘、不可召唤，并在回合结束时全部清扫。
 */
public final class SeekerEntities {
    private static EntityType<SeekerCarEntity> car;
    private static EntityType<SeekerCameraEntity> camera;
    private static boolean registered;

    private SeekerEntities() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        car = Registry.register(
                Registries.ENTITY_TYPE,
                SeekerRules.CAR_ENTITY_ID,
                EntityType.Builder.<SeekerCarEntity>create(SeekerCarEntity::new, SpawnGroup.MISC)
                        .dimensions(SeekerRules.CAR_WIDTH, SeekerRules.CAR_HEIGHT)
                        .eyeHeight(SeekerRules.CAR_EYE_HEIGHT)
                        .maxTrackingRange(SeekerRules.CAR_TRACKING_RANGE)
                        .trackingTickInterval(SeekerRules.CAR_TRACKING_INTERVAL)
                        .disableSaving()
                        .disableSummon()
                        .build(SeekerRules.CAR_ENTITY_ID.toString())
        );
        camera = Registry.register(
                Registries.ENTITY_TYPE,
                SeekerRules.CAMERA_ENTITY_ID,
                EntityType.Builder.<SeekerCameraEntity>create(SeekerCameraEntity::new, SpawnGroup.MISC)
                        .dimensions(SeekerRules.CAMERA_SIZE, SeekerRules.CAMERA_SIZE)
                        .maxTrackingRange(SeekerRules.CAMERA_TRACKING_RANGE)
                        .trackingTickInterval(SeekerRules.CAMERA_TRACKING_INTERVAL)
                        .disableSaving()
                        .disableSummon()
                        .build(SeekerRules.CAMERA_ENTITY_ID.toString())
        );
        registered = true;
    }

    public static EntityType<SeekerCarEntity> car() {
        if (car == null) {
            throw new IllegalStateException("Seeker entities are not registered yet");
        }
        return car;
    }

    public static EntityType<SeekerCameraEntity> camera() {
        if (camera == null) {
            throw new IllegalStateException("Seeker entities are not registered yet");
        }
        return camera;
    }
}
