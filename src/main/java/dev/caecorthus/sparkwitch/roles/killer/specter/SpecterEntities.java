package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Specter-owned gameplay entities: the placed ghost trigger and the short-lived visage hallucination. / 幽魂实体：鬼魂触发器与短命幻影。 */
public final class SpecterEntities {
    public static final Identifier GHOST_TRIGGER_ID = SparkWitch.id("ghost_trigger");
    public static final Identifier GHOST_VISAGE_ID = SparkWitch.id("ghost_visage");
    private static EntityType<GhostTriggerEntity> ghostTrigger;
    private static EntityType<GhostVisageEntity> ghostVisage;
    private static boolean registered;

    private SpecterEntities() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        ghostTrigger = Registry.register(
                Registries.ENTITY_TYPE,
                GHOST_TRIGGER_ID,
                EntityType.Builder.<GhostTriggerEntity>create(GhostTriggerEntity::new, SpawnGroup.MISC)
                        .dimensions(0.5F, 0.1F)
                        .maxTrackingRange(64)
                        .build(GHOST_TRIGGER_ID.toString())
        );
        // A 2 s client-rendered hallucination; never saved or summoned. / 2 秒的客户端渲染幻影；从不保存或召唤。
        ghostVisage = Registry.register(
                Registries.ENTITY_TYPE,
                GHOST_VISAGE_ID,
                EntityType.Builder.<GhostVisageEntity>create(GhostVisageEntity::new, SpawnGroup.MISC)
                        .dimensions(0.6F, 1.8F)
                        .maxTrackingRange(32)
                        .disableSaving()
                        .disableSummon()
                        .build(GHOST_VISAGE_ID.toString())
        );
        FabricDefaultAttributeRegistry.register(ghostVisage, GhostVisageEntity.createAttributes());
        registered = true;
    }

    public static EntityType<GhostTriggerEntity> ghostTrigger() {
        if (ghostTrigger == null) {
            throw new IllegalStateException("Specter entities are not registered yet");
        }
        return ghostTrigger;
    }

    public static EntityType<GhostVisageEntity> ghostVisage() {
        if (ghostVisage == null) {
            throw new IllegalStateException("Specter entities are not registered yet");
        }
        return ghostVisage;
    }
}
