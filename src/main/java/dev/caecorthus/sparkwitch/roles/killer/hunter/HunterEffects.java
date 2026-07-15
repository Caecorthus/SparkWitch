package dev.caecorthus.sparkwitch.roles.killer.hunter;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;

/** Registers the Hunter-owned fracture effect. / 注册由猎人模块持有的骨折效果。 */
public final class HunterEffects {
    private static RegistryEntry<StatusEffect> fracture;
    private static boolean registered;

    private HunterEffects() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        fracture = Registry.registerReference(Registries.STATUS_EFFECT, SparkWitch.id("fracture"), new FractureEffect());
        registered = true;
    }

    public static RegistryEntry<StatusEffect> fracture() {
        if (fracture == null) {
            throw new IllegalStateException("Hunter effects are not registered yet");
        }
        return fracture;
    }
}
