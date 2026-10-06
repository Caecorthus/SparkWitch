package dev.caecorthus.sparkwitch.roles.killer.hunter;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** Registers Hunter-owned gameplay entities. / 注册由猎人模块持有的玩法实体。 */
public final class HunterEntities {
    private static EntityType<HunterTrapEntity> hunterTrap;
    private static boolean registered;

    private HunterEntities() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        hunterTrap = Registry.register(
                Registries.ENTITY_TYPE,
                SparkWitch.id("hunter_trap"),
                EntityType.Builder.<HunterTrapEntity>create(HunterTrapEntity::new, SpawnGroup.MISC)
                        // 捕兽夹模型的实际范围约为 0.5 格宽、0.125 格高；
                        // 包围盒略微留出高度余量，避免模型超出实体包围盒后被视锥裁剪。
                        // The hitbox follows the placed model size with a small vertical margin.
                        .dimensions(0.5F, 0.14F)
                        .build("hunter_trap")
        );
        registered = true;
    }

    public static EntityType<HunterTrapEntity> hunterTrap() {
        if (hunterTrap == null) {
            throw new IllegalStateException("Hunter entities are not registered yet");
        }
        return hunterTrap;
    }
}
