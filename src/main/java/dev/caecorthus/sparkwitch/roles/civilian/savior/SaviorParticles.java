package dev.caecorthus.sparkwitch.roles.civilian.savior;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * The pistol's world muzzle flash particle. The sprite lives at
 * {@code assets/sparkwitch/textures/particle/gunshot.png} and is declared by {@code assets/sparkwitch/particles/gunshot.json},
 * so the asset can be replaced without touching code.
 * 手枪的世界枪口火焰粒子。贴图位于 {@code assets/sparkwitch/textures/particle/gunshot.png}，由
 * {@code assets/sparkwitch/particles/gunshot.json} 声明，替换素材无需改代码。
 */
public final class SaviorParticles {
    public static final SimpleParticleType GUNSHOT = Registry.register(
            Registries.PARTICLE_TYPE,
            SaviorRules.GUNSHOT_PARTICLE_ID,
            FabricParticleTypes.simple(true)
    );

    private static boolean registered;

    private SaviorParticles() {
    }

    public static synchronized void register() {
        // The static field registers the type; this call only documents the init order.
        // 静态字段完成粒子类型注册；该方法仅用于明确初始化顺序。
        registered = true;
    }
}
