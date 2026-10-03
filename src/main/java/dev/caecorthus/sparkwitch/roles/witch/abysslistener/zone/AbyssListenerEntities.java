package dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone;

import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssListenerRules;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Registers Abyss Listener gameplay entities; must run before SparkWitchItems so item code can capture the type.
 * 注册聆渊者的玩法实体；必须先于 SparkWitchItems 执行，以便物品代码可以获取该实体类型。
 */
public final class AbyssListenerEntities {
    private static EntityType<DeepDarkSporeFlaskEntity> deepDarkSporeFlask;
    private static boolean registered;

    private AbyssListenerEntities() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        // Vanilla thrown-item tracking (snowball-sized box, 4-chunk range, 10-tick updates), like the Shock Device.
        // 沿用原版投掷物追踪参数（雪球大小碰撞箱、4 区块范围、每 10 刻更新），与电击装置相同。
        deepDarkSporeFlask = Registry.register(
                Registries.ENTITY_TYPE,
                AbyssListenerRules.FLASK_ENTITY_ID,
                EntityType.Builder.<DeepDarkSporeFlaskEntity>create(DeepDarkSporeFlaskEntity::new, SpawnGroup.MISC)
                        .dimensions(0.25F, 0.25F)
                        .maxTrackingRange(4)
                        .trackingTickInterval(10)
                        .build(AbyssListenerRules.FLASK_ENTITY_ID.toString())
        );
        registered = true;
    }

    public static EntityType<DeepDarkSporeFlaskEntity> deepDarkSporeFlask() {
        if (deepDarkSporeFlask == null) {
            throw new IllegalStateException("Abyss Listener entities are not registered yet");
        }
        return deepDarkSporeFlask;
    }
}
