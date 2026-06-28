package dev.caecorthus.sparkwitch;

import dev.caecorthus.sparkwitch.entity.CapsuleEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class SparkWitchEntities {
    public static final Identifier CAPSULE_ID = SparkWitch.id("capsule");
    private static EntityType<CapsuleEntity> capsule;
    private static boolean registered;

    private SparkWitchEntities() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        capsule = Registry.register(
                Registries.ENTITY_TYPE,
                CAPSULE_ID,
                EntityType.Builder.<CapsuleEntity>create(CapsuleEntity::new, SpawnGroup.MISC)
                        .dimensions(0.25F, 0.25F)
                        .maxTrackingRange(4)
                        .trackingTickInterval(10)
                        .build(CAPSULE_ID.toString())
        );
        registered = true;
    }

    public static EntityType<CapsuleEntity> capsule() {
        if (capsule == null) {
            throw new IllegalStateException("SparkWitch entities are not registered yet");
        }
        return capsule;
    }
}
