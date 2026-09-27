package dev.caecorthus.sparkwitch.roles.civilian.controlexpert;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Registers Control Expert gameplay entities; must run before SparkWitchItems so item code can capture the type.
 * 注册控场专家的玩法实体；必须先于 SparkWitchItems 执行，以便物品代码可以获取该实体类型。
 */
public final class ControlExpertEntities {
    private static EntityType<ShockDeviceEntity> shockDevice;
    private static boolean registered;

    private ControlExpertEntities() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        // Vanilla thrown-item tracking (snowball-sized box, 4-chunk range, 10-tick updates).
        // 沿用原版投掷物追踪参数（雪球大小碰撞箱、4 区块范围、每 10 刻更新）。
        shockDevice = Registry.register(
                Registries.ENTITY_TYPE,
                ControlExpertRules.SHOCK_DEVICE_ID,
                EntityType.Builder.<ShockDeviceEntity>create(ShockDeviceEntity::new, SpawnGroup.MISC)
                        .dimensions(0.25F, 0.25F)
                        .maxTrackingRange(4)
                        .trackingTickInterval(10)
                        .build(ControlExpertRules.SHOCK_DEVICE_ID.toString())
        );
        registered = true;
    }

    public static EntityType<ShockDeviceEntity> shockDevice() {
        if (shockDevice == null) {
            throw new IllegalStateException("Control Expert entities are not registered yet");
        }
        return shockDevice;
    }
}
