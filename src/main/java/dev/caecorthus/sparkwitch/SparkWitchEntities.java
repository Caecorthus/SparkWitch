package dev.caecorthus.sparkwitch;

import dev.caecorthus.sparkwitch.entity.NinjaShurikenEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class SparkWitchEntities {
    public static final Identifier NINJA_SHURIKEN_ID = SparkWitch.id("ninja_shuriken");

    private static EntityType<NinjaShurikenEntity> ninjaShuriken;
    private static boolean registered;

    private SparkWitchEntities() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        ninjaShuriken = Registry.register(
                Registries.ENTITY_TYPE,
                NINJA_SHURIKEN_ID,
                EntityType.Builder.<NinjaShurikenEntity>create(NinjaShurikenEntity::new, SpawnGroup.MISC)
                        .dimensions(0.25F, 0.25F)
                        .maxTrackingRange(4)
                        .trackingTickInterval(1)
                        .build(NINJA_SHURIKEN_ID.toString())
        );
        registered = true;
    }

    public static EntityType<NinjaShurikenEntity> ninjaShuriken() {
        if (ninjaShuriken == null) {
            throw new IllegalStateException("SparkWitch entities are not registered yet");
        }
        return ninjaShuriken;
    }
}
