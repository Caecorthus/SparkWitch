package dev.caecorthus.sparkwitch.roles.civilian.saint.flash;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Registers the thrown Holy Flash entity; must run before SparkWitchItems so item code can capture the type.
 * 注册投出的圣光弹实体；必须先于 SparkWitchItems 执行，以便物品代码可以获取该实体类型。
 */
public final class HolyFlashEntities {
    private static EntityType<HolyFlashEntity> holyFlash;
    private static boolean registered;

    private HolyFlashEntities() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        // Vanilla thrown-item tracking (snowball-sized box, 4-chunk range, 10-tick updates).
        // 沿用原版投掷物追踪参数（雪球大小碰撞箱、4 区块范围、每 10 刻更新）。
        holyFlash = Registry.register(
                Registries.ENTITY_TYPE,
                HolyFlashRules.ENTITY_ID,
                EntityType.Builder.<HolyFlashEntity>create(HolyFlashEntity::new, SpawnGroup.MISC)
                        .dimensions(0.25F, 0.25F)
                        .maxTrackingRange(4)
                        .trackingTickInterval(10)
                        .build(HolyFlashRules.ENTITY_ID.toString())
        );
        registered = true;
    }

    public static EntityType<HolyFlashEntity> holyFlash() {
        if (holyFlash == null) {
            throw new IllegalStateException("Holy Flash entities are not registered yet");
        }
        return holyFlash;
    }
}
