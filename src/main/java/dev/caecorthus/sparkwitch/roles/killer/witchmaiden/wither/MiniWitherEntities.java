package dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** The Mini Wither skull entity type. / 小凋灵头颅实体类型。 */
public final class MiniWitherEntities {
    private static EntityType<MiniWitherSkullEntity> skull;
    private static boolean registered;

    private MiniWitherEntities() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        // Tracked every tick with velocity: the crimson skull covers 4 blocks a tick.
        // 每刻随速度一起追踪：赤首每刻飞 4 格。
        skull = Registry.register(
                Registries.ENTITY_TYPE,
                MiniWitherRules.SKULL_ENTITY_ID,
                EntityType.Builder.<MiniWitherSkullEntity>create(MiniWitherSkullEntity::new, SpawnGroup.MISC)
                        .dimensions(0.25F, 0.25F)
                        .maxTrackingRange(4)
                        .trackingTickInterval(1)
                        .alwaysUpdateVelocity(true)
                        .disableSaving()
                        .disableSummon()
                        .build(MiniWitherRules.SKULL_ENTITY_ID.toString())
        );
        registered = true;
    }

    public static EntityType<MiniWitherSkullEntity> skull() {
        if (skull == null) {
            throw new IllegalStateException("Mini Wither entities are not registered yet");
        }
        return skull;
    }
}
