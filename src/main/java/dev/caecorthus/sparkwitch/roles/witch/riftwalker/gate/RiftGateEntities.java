package dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerRules;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Registers the Rift Gate entity type; must run before SparkWitchItems so item code can capture the type. Gates never
 * save to disk and cannot be summoned; the server registry is their source of truth and sweeps them at round edges.
 * The type box is the unrotated 1 × 2 default; the entity narrows it to {@link RiftwalkerRules#GATE_DEPTH} per facing.
 * 注册裂隙门实体类型；必须先于 SparkWitchItems 执行，以便物品代码获取该类型。门从不存盘、不可召唤；
 * 服务端登记表是唯一真相，并在对局边界清扫。类型碰撞箱为未旋转的 1 × 2 默认值；实体按朝向收窄到 {@link RiftwalkerRules#GATE_DEPTH}。
 */
public final class RiftGateEntities {
    private static EntityType<RiftGateEntity> riftGate;
    private static boolean registered;

    private RiftGateEntities() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        // Static and long-range like the Seeker camera: 8-chunk tracking, 20-tick updates. / 与搜寻者摄像头一样静态远距追踪。
        riftGate = Registry.register(
                Registries.ENTITY_TYPE,
                RiftwalkerRules.GATE_ENTITY_ID,
                EntityType.Builder.<RiftGateEntity>create(RiftGateEntity::new, SpawnGroup.MISC)
                        .dimensions(RiftwalkerRules.GATE_WIDTH, RiftwalkerRules.GATE_HEIGHT)
                        .maxTrackingRange(RiftwalkerRules.GATE_TRACKING_RANGE)
                        .trackingTickInterval(RiftwalkerRules.GATE_TRACKING_INTERVAL)
                        .disableSaving()
                        .disableSummon()
                        .build(RiftwalkerRules.GATE_ENTITY_ID.toString())
        );
        registered = true;
    }

    public static EntityType<RiftGateEntity> riftGate() {
        if (riftGate == null) {
            throw new IllegalStateException("Riftwalker entities are not registered yet");
        }
        return riftGate;
    }
}
