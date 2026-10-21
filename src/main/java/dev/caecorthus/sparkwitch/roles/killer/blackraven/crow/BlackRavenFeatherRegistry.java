package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Registers the Raven Form world content with fixed ids (stable contract): the display-only Homing Feather item and its
 * marker entity (both {@code sparkwitch:homing_feather}) and the landing particle {@code sparkwitch:black_feather}.
 * Runs from {@code SparkWitch} during common initialization, so every registry entry exists on both sides. The item is
 * deliberately kept out of {@code SparkWitchItems} and out of the SparkWitch creative tab.
 * 以固定 id 注册化鸦的世界内容（稳定契约）：仅用于显示的归羽物品及其标记实体（均为 {@code sparkwitch:homing_feather}），以及落地粒子
 * {@code sparkwitch:black_feather}。在通用初始化阶段由 {@code SparkWitch} 调用，因此每个注册项在两端都存在。该物品刻意不放进
 * {@code SparkWitchItems}，也不进入 SparkWitch 创造物品栏。
 */
public final class BlackRavenFeatherRegistry {
    private static Item homingFeather;
    private static EntityType<BlackRavenFeatherEntity> featherMarker;
    private static SimpleParticleType blackFeather;
    private static boolean registered;

    private BlackRavenFeatherRegistry() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        homingFeather = Registry.register(Registries.ITEM, BlackRavenCrowFeatherRules.FEATHER_ID,
                new BlackRavenHomingFeatherItem(BlackRavenHomingFeatherItem.createSettings()));
        featherMarker = Registry.register(
                Registries.ENTITY_TYPE,
                BlackRavenCrowFeatherRules.FEATHER_ID,
                EntityType.Builder.<BlackRavenFeatherEntity>create(BlackRavenFeatherEntity::new, SpawnGroup.MISC)
                        .dimensions(BlackRavenCrowFeatherRules.WIDTH, BlackRavenCrowFeatherRules.HEIGHT)
                        .maxTrackingRange(BlackRavenCrowFeatherRules.TRACKING_RANGE)
                        .trackingTickInterval(BlackRavenCrowFeatherRules.TRACKING_INTERVAL)
                        .makeFireImmune()
                        .disableSaving()
                        .disableSummon()
                        .build(BlackRavenCrowFeatherRules.FEATHER_ID.toString())
        );
        blackFeather = Registry.register(Registries.PARTICLE_TYPE, BlackRavenCrowFeatherRules.PARTICLE_ID,
                FabricParticleTypes.simple());
        registered = true;
    }

    public static Item homingFeather() {
        return require(homingFeather);
    }

    public static EntityType<BlackRavenFeatherEntity> featherMarker() {
        return require(featherMarker);
    }

    public static SimpleParticleType blackFeather() {
        return require(blackFeather);
    }

    /** Whether {@code item} is the Homing Feather (false before registration). / 是否为归羽（注册前为 false）。 */
    public static boolean isHomingFeather(Item item) {
        return item != null && item == homingFeather;
    }

    private static <T> T require(T value) {
        if (value == null) {
            throw new IllegalStateException("Black Raven feather content is not registered yet");
        }
        return value;
    }
}
