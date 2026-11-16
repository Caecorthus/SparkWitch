package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Specter-owned particle types. {@code sparkwitch:joss_paper} (fixed id, stable contract) is the falling paper coin of
 * the Otherworld's Yin-realm look; it is spawned client-side only, but the type is registered during common
 * initialization from {@code SparkWitch} so the particle registry matches on both sides. Sprites are declared by
 * {@code assets/sparkwitch/particles/joss_paper.json}.
 * 幽魂的粒子类型。{@code sparkwitch:joss_paper}（固定 id，稳定契约）是里世界「阴间」观感中飘落的纸钱；它只在客户端生成，但类型在通用
 * 初始化阶段由 {@code SparkWitch} 注册，使两端的粒子注册表一致。贴图由 {@code assets/sparkwitch/particles/joss_paper.json} 声明。
 */
public final class SpecterParticles {
    public static final Identifier JOSS_PAPER_ID = SparkWitch.id("joss_paper");
    private static SimpleParticleType jossPaper;
    private static boolean registered;

    private SpecterParticles() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        jossPaper = Registry.register(Registries.PARTICLE_TYPE, JOSS_PAPER_ID, FabricParticleTypes.simple());
        registered = true;
    }

    public static SimpleParticleType jossPaper() {
        if (jossPaper == null) {
            throw new IllegalStateException("Specter particles are not registered yet");
        }
        return jossPaper;
    }
}
